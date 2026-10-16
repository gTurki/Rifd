package com.example.rifd.Service;

import com.example.rifd.Api.ApiException;
import com.example.rifd.DTOOut.RecommendationDTOOut;
import com.example.rifd.Entity.Incident;
import com.example.rifd.Entity.Playbook;
import com.example.rifd.Entity.Recommendation;
import com.example.rifd.Entity.Task;
import com.example.rifd.Enum.ActionType;
import com.example.rifd.Enum.ActorType;
import com.example.rifd.Enum.EntityType;
import com.example.rifd.Enum.IncidentCategory;
import com.example.rifd.Enum.RecommendationStatus;
import com.example.rifd.Enum.Severity;
import com.example.rifd.Enum.StaffLevel;
import com.example.rifd.Enum.StaffSpecialty;
import com.example.rifd.Events.IncidentCreatedEvent;
import com.example.rifd.Events.IncidentReopenedEvent;
import com.example.rifd.Events.IncidentResolvedEvent;
import com.example.rifd.Events.IncidentSeverityChangedEvent;
import com.example.rifd.Mapper.DTOMapper;
import com.example.rifd.Repository.RecommendationRepository;
import com.example.rifd.Repository.StaffRepository;
import com.example.rifd.Repository.TaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Slf4j
@Service
public class RecommendationService {

    // what the incident needs right now
    private record Plan(Playbook playbook, String actionText, String reasoning, StaffSpecialty specialty,
                        StaffLevel level, int staffCount, double confidence, ActorType decidedBy) {
    }

    // everything the AI call needs, read in a short transaction (the AI call itself runs outside any transaction)
    private record Snapshot(Long incidentId, String title, String summary, IncidentCategory category,
                            Severity severity, List<Playbook> playbooks) {
    }

    private static final double FALLBACK_CONFIDENCE = 0.5;

    private final RecommendationRepository recommendationRepository;
    private final TaskRepository taskRepository;
    private final StaffRepository staffRepository;
    private final PlaybookService playbookService;
    private final IncidentService incidentService;
    private final AssignmentService assignmentService;
    private final ActivityLogService activityLogService;
    private final BroadcastService broadcastService;
    private final AiClient aiClient;
    private final TransactionTemplate tx;
    // HIGH incidents: the plan is accepted and staffed automatically (assignment.auto-assign-high)
    private final boolean autoAssignHigh;

    // one lock per incident: the async "created" and "severity changed" listeners, and the operator's
    // accept/reject, can arrive at the same time for the same incident
    private final Map<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

    public RecommendationService(RecommendationRepository recommendationRepository, TaskRepository taskRepository,
                                 StaffRepository staffRepository, PlaybookService playbookService,
                                 IncidentService incidentService, AssignmentService assignmentService,
                                 ActivityLogService activityLogService, BroadcastService broadcastService,
                                 AiClient aiClient, PlatformTransactionManager transactionManager,
                                 @Value("${assignment.auto-assign-high:true}") boolean autoAssignHigh) {
        this.autoAssignHigh = autoAssignHigh;
        this.recommendationRepository = recommendationRepository;
        this.taskRepository = taskRepository;
        this.staffRepository = staffRepository;
        this.playbookService = playbookService;
        this.incidentService = incidentService;
        this.assignmentService = assignmentService;
        this.activityLogService = activityLogService;
        this.broadcastService = broadcastService;
        this.aiClient = aiClient;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // ---------------- listeners ----------------

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIncidentCreated(IncidentCreatedEvent event) {
        safely(event.incidentId(), () -> regenerate(event.incidentId(), true));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSeverityChanged(IncidentSeverityChangedEvent event) {
        safely(event.incidentId(), () -> regenerate(event.incidentId(), false));
    }

    // a closed incident has nothing left to decide: PENDING -> SUPERSEDED
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIncidentResolved(IncidentResolvedEvent event) {
        safely(event.incidentId(), () -> withLock(event.incidentId(), () -> tx.execute(s -> {
            // reopened before this listener ran: keep the new plan
            if (!IncidentService.isClosed(incidentService.getIncident(event.incidentId()).getStatus())) return null;
            supersedePending(event.incidentId(), "Incident closed as " + event.finalStatus());
            return null;
        })));
    }

    // reopened -> fresh plan that counts whoever is still on it
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIncidentReopened(IncidentReopenedEvent event) {
        safely(event.incidentId(), () -> regenerate(event.incidentId(), false));
    }

    // ---------------- API ----------------

    @Transactional(readOnly = true)
    public List<RecommendationDTOOut> byIncident(Long incidentId) {
        incidentService.getIncident(incidentId);
        return recommendationRepository.findByIncidentIdOrderByCreatedAtDesc(incidentId).stream()
                .map(DTOMapper::toDTO).toList();
    }

    // ACCEPTED -> automatic assignment of the nearest suitable staff
    public RecommendationDTOOut accept(Long recommendationId, Long actorId) {
        Long incidentId = incidentIdOf(recommendationId);
        return withLock(incidentId, () -> tx.execute(s -> {
            incidentService.validateOperator(actorId);
            Recommendation rec = getPending(recommendationId);
            Incident incident = incidentService.getOpenIncident(incidentId); // 400 if closed
            incidentService.autoAcknowledge(incident, actorId);

            rec.setStatus(RecommendationStatus.ACCEPTED);
            rec.setDecidedBy(actorId == null ? null : staffRepository.getReferenceById(actorId));
            rec.setDecidedAt(LocalDateTime.now());
            recommendationRepository.save(rec);

            activityLogService.log(EntityType.RECOMMENDATION, rec.getId(), incidentId, ActionType.ACCEPTED,
                    ActorType.HUMAN, actorId, RecommendationStatus.PENDING.name(), RecommendationStatus.ACCEPTED.name(),
                    "Operator accepted: " + shortText(rec.getActionText()));

            assignmentService.assignFromRecommendation(rec, actorId);

            RecommendationDTOOut out = DTOMapper.toDTO(rec);
            broadcastService.send(BroadcastService.RECOMMENDATIONS, "RECOMMENDATION_UPDATED", out);
            return out;
        }));
    }

    // REJECTED -> the operator assigns manually (POST /tasks); the reason is kept for learning false alarms
    public RecommendationDTOOut reject(Long recommendationId, String reason, Long actorId) {
        Long incidentId = incidentIdOf(recommendationId);
        return withLock(incidentId, () -> tx.execute(s -> {
            incidentService.validateOperator(actorId);
            Recommendation rec = getPending(recommendationId);
            incidentService.autoAcknowledge(incidentService.getOpenIncident(incidentId), actorId);

            rec.setStatus(RecommendationStatus.REJECTED);
            rec.setRejectionReason(reason.trim());
            rec.setDecidedBy(actorId == null ? null : staffRepository.getReferenceById(actorId));
            rec.setDecidedAt(LocalDateTime.now());
            recommendationRepository.save(rec);

            activityLogService.log(EntityType.RECOMMENDATION, rec.getId(), incidentId, ActionType.REJECTED,
                    ActorType.HUMAN, actorId, RecommendationStatus.PENDING.name(), RecommendationStatus.REJECTED.name(),
                    "Rejected: " + reason.trim());

            RecommendationDTOOut out = DTOMapper.toDTO(rec);
            broadcastService.send(BroadcastService.RECOMMENDATIONS, "RECOMMENDATION_UPDATED", out);
            return out;
        }));
    }

    // operator asks for a fresh plan (after a reject, a manual assignment, or a plan that was not fully staffed)
    public List<RecommendationDTOOut> regenerateNow(Long incidentId) {
        tx.execute(s -> incidentService.getOpenIncident(incidentId)); // 400 if missing or closed
        regenerate(incidentId, false); // supersedes PENDING, counts the staff already on it
        return tx.execute(s -> byIncident(incidentId));
    }

    // ---------------- generation ----------------

    private void regenerate(Long incidentId, boolean onCreate) {
        withLock(incidentId, () -> {
            // 1) short read transaction
            Snapshot snap = tx.execute(s -> snapshot(incidentId, onCreate));
            if (snap == null) return null;

            // 2) AI suggestion (optional) outside any transaction, then 3) write
            Optional<AiClient.RecommendResponse> ai = askAi(snap);
            Long recId = tx.execute(s -> write(incidentId, snap, ai));

            // 4) HIGH: nobody waits for the operator, the plan is accepted and staffed right away.
            //    Own transaction: if it fails, the plan stays PENDING for the operator
            if (recId != null && autoAssignHigh && snap.severity() == Severity.HIGH) {
                try {
                    tx.execute(s -> {
                        autoAccept(recId, incidentId);
                        return null;
                    });
                } catch (Exception e) {
                    log.warn("Auto-assignment of HIGH incident #{} failed, left for the operator: {}", incidentId, e.getMessage());
                }
            }
            return null;
        });
    }

    // null = nothing to do
    private Snapshot snapshot(Long incidentId, boolean onCreate) {
        Incident incident = incidentService.getIncident(incidentId);
        if (IncidentService.isClosed(incident.getStatus())) return null;
        // "created" arriving after a "severity changed" listener already produced the plan
        if (onCreate && !recommendationRepository.findByIncidentIdOrderByCreatedAtDesc(incidentId).isEmpty()) return null;

        List<Playbook> playbooks = new ArrayList<>(playbookService.applicable(incident.getCategory(), incident.getSeverity()));
        if (playbooks.isEmpty()) {
            playbooks.addAll(playbookService.applicable(IncidentCategory.OTHER, incident.getSeverity()));
        }
        return new Snapshot(incidentId, incident.getTitle(), incident.getSummary(), incident.getCategory(),
                incident.getSeverity(), playbooks);
    }

    private Optional<AiClient.RecommendResponse> askAi(Snapshot snap) {
        if (!aiClient.isEnabled() || snap.playbooks().isEmpty()) return Optional.empty();
        return aiClient.recommend(
                new AiClient.RecommendIncident(snap.incidentId(), snap.title(), snap.summary(),
                        snap.category() == null ? null : snap.category().name(),
                        snap.severity() == null ? null : snap.severity().name()),
                snap.playbooks().stream()
                        .map(p -> new AiClient.RecommendPlaybook(p.getId(), p.getTitle(), p.getSteps())).toList());
    }

    // the new PENDING recommendation's id, or null when none was needed
    private Long write(Long incidentId, Snapshot snap, Optional<AiClient.RecommendResponse> ai) {
        Incident incident = incidentService.getIncident(incidentId);
        if (IncidentService.isClosed(incident.getStatus())) return null;
        // the severity moved again while the AI was thinking: the next queued event rebuilds it
        if (incident.getSeverity() != snap.severity()) return null;

        Plan plan = buildPlan(incident, snap.playbooks(), ai);

        supersedePending(incidentId, "Superseded: severity is now " + incident.getSeverity());

        // staff already working on it (accepted plan or manual assignment) count toward the new plan
        long covering = taskRepository.findByIncidentIdAndStatusNotIn(incidentId,
                        List.of(com.example.rifd.Enum.TaskStatus.DONE, com.example.rifd.Enum.TaskStatus.REJECTED,
                                com.example.rifd.Enum.TaskStatus.CANCELLED)).stream()
                .filter(t -> covers(t, plan))
                .count();
        int missing = plan.staffCount() - (int) covering;
        if (missing <= 0) {
            if (covering > 0) {
                activityLogService.log(EntityType.INCIDENT, incidentId, incidentId, ActionType.STATUS_CHANGED,
                        ActorType.SYSTEM, null, null, null,
                        "Severity " + incident.getSeverity() + ": current staff already cover the plan");
            }
            return null;
        }

        Recommendation rec = new Recommendation();
        rec.setIncident(incident);
        rec.setPlaybook(plan.playbook());
        rec.setActionText(plan.actionText());
        rec.setReasoning(covering > 0
                ? plan.reasoning() + " | reinforcement: " + covering + " already on site, " + missing + " more needed"
                : plan.reasoning());
        rec.setRequiredSpecialty(plan.specialty());
        rec.setRequiredLevel(plan.level());
        rec.setStaffCount(missing);
        rec.setConfidence(plan.confidence());
        rec.setStatus(RecommendationStatus.PENDING);
        recommendationRepository.save(rec);

        activityLogService.log(EntityType.RECOMMENDATION, rec.getId(), incidentId, ActionType.CREATED,
                plan.decidedBy(), null, null, plan.specialty() + "/" + plan.level() + " x" + missing,
                (plan.playbook() == null ? "No playbook" : "Playbook: " + plan.playbook().getTitle())
                        + String.format(Locale.ROOT, " (confidence %.2f)", plan.confidence()));

        broadcastService.send(BroadcastService.RECOMMENDATIONS, "RECOMMENDATION_CREATED", DTOMapper.toDTO(rec));
        return rec.getId();
    }

    // same as the operator's accept, by the SYSTEM. The incident is NOT acknowledged: no human has seen it yet
    private void autoAccept(Long recommendationId, Long incidentId) {
        Recommendation rec = recommendationRepository.findById(recommendationId).orElse(null);
        if (rec == null || rec.getStatus() != RecommendationStatus.PENDING) return; // the operator was faster
        Incident incident = incidentService.getIncidentForUpdate(incidentId);
        if (IncidentService.isClosed(incident.getStatus()) || incident.getSeverity() != Severity.HIGH) return;

        rec.setStatus(RecommendationStatus.ACCEPTED);
        rec.setDecidedAt(LocalDateTime.now());
        recommendationRepository.save(rec);

        activityLogService.log(EntityType.RECOMMENDATION, rec.getId(), incidentId, ActionType.ACCEPTED,
                ActorType.SYSTEM, null, RecommendationStatus.PENDING.name(), RecommendationStatus.ACCEPTED.name(),
                "Auto-accepted: HIGH incident, assigned without waiting for the operator");

        assignmentService.assignFromRecommendation(rec, null);
        broadcastService.send(BroadcastService.RECOMMENDATIONS, "RECOMMENDATION_UPDATED", DTOMapper.toDTO(rec));
    }

    private Plan buildPlan(Incident incident, List<Playbook> playbooks, Optional<AiClient.RecommendResponse> ai) {
        Playbook playbook = playbooks.isEmpty() ? null : playbooks.get(0);
        String actionText = null;
        String reasoning = null;
        double confidence = FALLBACK_CONFIDENCE;
        ActorType decidedBy = ActorType.SYSTEM;

        // AI answer is used only if it picked one of the playbooks we offered
        if (ai.isPresent() && ai.get().playbookId() != null) {
            Optional<Playbook> chosen = playbooks.stream()
                    .filter(p -> p.getId().equals(ai.get().playbookId())).findFirst();
            if (chosen.isPresent()) {
                playbook = chosen.get();
                actionText = ai.get().actionText();
                reasoning = ai.get().reasoning();
                confidence = ai.get().confidence() == null ? FALLBACK_CONFIDENCE
                        : Math.max(0, Math.min(1, ai.get().confidence()));
                decidedBy = ActorType.AI;
            }
        }

        // a playbook of another category (generic OTHER) must not decide WHO goes: the category does
        boolean ownCategory = playbook != null && playbook.getCategory() == incident.getCategory();
        StaffSpecialty specialty = ownCategory && playbook.getRequiredSpecialty() != null
                ? playbook.getRequiredSpecialty()
                : PlaybookService.defaultSpecialty(incident.getCategory());
        StaffLevel level = playbook != null && playbook.getRequiredLevel() != null
                ? playbook.getRequiredLevel() : StaffLevel.CERTIFIED;
        if (incident.getSeverity() == Severity.LOW) level = StaffLevel.ASSISTANT; // simple incidents -> assistant
        int count = playbook != null && playbook.getRecommendedStaffCount() != null
                ? Math.max(1, playbook.getRecommendedStaffCount()) : 1;

        if (!StringUtils.hasText(actionText)) {
            actionText = playbook != null && StringUtils.hasText(playbook.getSteps())
                    ? playbook.getSteps()
                    : "Dispatch the nearest " + specialty + " staff to " + incident.getCode() + " and report back.";
        }
        if (!StringUtils.hasText(reasoning)) {
            reasoning = playbook != null
                    ? "Playbook match by category and severity (" + incident.getCategory() + ", " + incident.getSeverity() + ")"
                    : "No playbook for " + incident.getCategory() + "; default response by category";
        }
        return new Plan(playbook, actionText, reasoning, specialty, level, count, confidence, decidedBy);
    }

    // a task covers the plan if same specialty and the level is enough (CERTIFIED covers anything)
    private static boolean covers(Task task, Plan plan) {
        return task.getStaff().getSpecialty() == plan.specialty()
                && (task.getStaff().getLevel() == StaffLevel.CERTIFIED || plan.level() == StaffLevel.ASSISTANT);
    }

    private void supersedePending(Long incidentId, String reason) {
        for (Recommendation old : recommendationRepository.findByIncidentIdAndStatus(incidentId, RecommendationStatus.PENDING)) {
            old.setStatus(RecommendationStatus.SUPERSEDED);
            recommendationRepository.save(old);
            activityLogService.log(EntityType.RECOMMENDATION, old.getId(), incidentId, ActionType.STATUS_CHANGED,
                    ActorType.SYSTEM, null, RecommendationStatus.PENDING.name(), RecommendationStatus.SUPERSEDED.name(), reason);
            broadcastService.send(BroadcastService.RECOMMENDATIONS, "RECOMMENDATION_UPDATED", DTOMapper.toDTO(old));
        }
    }

    // ---------------- helpers ----------------

    private Recommendation getPending(Long recommendationId) {
        Recommendation rec = recommendationRepository.findById(recommendationId)
                .orElseThrow(() -> new ApiException("Recommendation not found"));
        if (rec.getStatus() != RecommendationStatus.PENDING) {
            throw new ApiException("Recommendation is already " + rec.getStatus());
        }
        return rec;
    }

    private Long incidentIdOf(Long recommendationId) {
        Long id = tx.execute(s -> recommendationRepository.findById(recommendationId)
                .map(r -> r.getIncident().getId()).orElse(null));
        if (id == null) throw new ApiException("Recommendation not found");
        return id;
    }

    private <T> T withLock(Long incidentId, Supplier<T> work) {
        ReentrantLock lock = locks.computeIfAbsent(incidentId, k -> new ReentrantLock(true));
        lock.lock();
        try {
            return work.get();
        } finally {
            lock.unlock();
        }
    }

    private void safely(Long incidentId, Runnable work) {
        try {
            work.run();
        } catch (Exception e) {
            log.error("Recommendation handling failed for incident #{}", incidentId, e);
        }
    }

    private static String shortText(String text) {
        if (text == null) return "";
        return text.length() <= 80 ? text : text.substring(0, 79) + "…";
    }
}
