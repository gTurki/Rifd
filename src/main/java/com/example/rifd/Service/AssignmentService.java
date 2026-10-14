package com.example.rifd.Service;

import com.example.rifd.Api.ApiException;
import com.example.rifd.DTOOut.CandidateDTOOut;
import com.example.rifd.DTOOut.TaskDTOOut;
import com.example.rifd.Entity.Incident;
import com.example.rifd.Entity.Recommendation;
import com.example.rifd.Entity.Staff;
import com.example.rifd.Entity.Task;
import com.example.rifd.Enum.ActionType;
import com.example.rifd.Enum.ActorType;
import com.example.rifd.Enum.EntityType;
import com.example.rifd.Enum.IncidentStatus;
import com.example.rifd.Enum.NotificationType;
import com.example.rifd.Enum.RecommendationStatus;
import com.example.rifd.Enum.Severity;
import com.example.rifd.Enum.StaffLevel;
import com.example.rifd.Enum.StaffRole;
import com.example.rifd.Enum.StaffSpecialty;
import com.example.rifd.Enum.StaffStatus;
import com.example.rifd.Enum.TaskStatus;
import com.example.rifd.Mapper.DTOMapper;
import com.example.rifd.Repository.RecommendationRepository;
import com.example.rifd.Repository.StaffRepository;
import com.example.rifd.Repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class AssignmentService {

    // the requirement a set of tasks has to cover
    public record Requirement(StaffSpecialty specialty, StaffLevel level, int count) {
    }

    // a ranked staff member; distance = null when the position is unknown
    public record Pick(Staff staff, Double distance, boolean escort) {
    }

    private final StaffRepository staffRepository;
    private final TaskRepository taskRepository;
    private final RecommendationRepository recommendationRepository;
    private final StaffService staffService;
    private final IncidentService incidentService;
    private final NotificationService notificationService;
    private final ActivityLogService activityLogService;
    private final BroadcastService broadcastService;

    @Value("${assignment.walking-speed-mps:1.3}")
    private double walkingSpeed;

    // ---------------- preview ----------------

    // nearest suitable staff WITHOUT assigning (GET /incidents/{id}/candidates)
    @Transactional(readOnly = true)
    public List<CandidateDTOOut> candidates(Long incidentId) {
        Incident incident = incidentService.getIncident(incidentId);
        Requirement req = currentRequirement(incident);
        Set<Long> excluded = staffOnIncident(incidentId);

        List<Pick> main = rank(incident, req.specialty(), req.level(), excluded, false);
        List<CandidateDTOOut> out = new ArrayList<>(main.stream().map(this::toCandidate).toList());

        if (incident.getSeverity() == Severity.HIGH) {
            Set<Long> taken = new HashSet<>(excluded);
            main.stream().limit(req.count()).forEach(p -> taken.add(p.staff().getId()));
            rank(incident, req.specialty(), StaffLevel.ASSISTANT, taken, true).stream()
                    .filter(p -> p.staff().getLevel() == StaffLevel.ASSISTANT)
                    .findFirst()
                    .ifPresent(p -> out.add(toCandidate(new Pick(p.staff(), p.distance(), true))));
        }
        return out;
    }

    // ---------------- automatic assignment (recommendation accepted) ----------------

    @Transactional
    public List<TaskDTOOut> assignFromRecommendation(Recommendation rec, Long operatorId) {
        Incident incident = rec.getIncident();
        Requirement req = new Requirement(rec.getRequiredSpecialty(), rec.getRequiredLevel(),
                rec.getStaffCount() == null ? 1 : Math.max(1, rec.getStaffCount()));

        Set<Long> excluded = staffOnIncident(incident.getId());
        List<Task> created = new ArrayList<>();
        String details = "Nearest available staff, recommendation #" + rec.getId() + " accepted";

        for (Pick pick : rank(incident, req.specialty(), req.level(), excluded, false)) {
            if (created.size() >= req.count()) break;
            if (!staffService.tryClaim(pick.staff())) continue; // someone else took this person
            created.add(createTask(incident, pick, rec, operatorId, ActorType.SYSTEM, null, details, null));
            excluded.add(pick.staff().getId());
        }
        int mainAssigned = created.size();

        // HIGH: one assistant of the same specialty escorts the certified staff (if none is on it already)
        if (incident.getSeverity() == Severity.HIGH && mainAssigned > 0 && !hasActiveAssistant(incident.getId(), req.specialty())) {
            for (Pick pick : rank(incident, req.specialty(), StaffLevel.ASSISTANT, excluded, true)) {
                if (pick.staff().getLevel() != StaffLevel.ASSISTANT) continue; // escort must be an assistant
                if (!staffService.tryClaim(pick.staff())) continue;
                created.add(createTask(incident, new Pick(pick.staff(), pick.distance(), true), rec, operatorId,
                        ActorType.SYSTEM, null, "Escort assistant for a HIGH incident", "Escort"));
                break;
            }
        }

        if (mainAssigned < req.count()) {
            escalate(incident, String.format(Locale.ROOT, "Only %d of %d %s (%s) staff available",
                    mainAssigned, req.count(), req.specialty(), req.level()));
        }
        if (!created.isEmpty()) {
            incidentService.changeStatus(incident.getId(), IncidentStatus.ASSIGNED, ActorType.SYSTEM, null);
        }
        return created.stream().map(DTOMapper::toDTO).toList();
    }

    // ---------------- manual assignment (POST /tasks) ----------------

    @Transactional
    public TaskDTOOut assignManual(Long incidentId, Long staffId, String notes, Long operatorId) {
        incidentService.validateActor(operatorId);
        Incident incident = incidentService.getOpenIncident(incidentId);
        Staff staff = staffService.getStaff(staffId);

        if (staff.getRole() != StaffRole.FIELD) throw new ApiException("Only field staff can receive tasks");
        if (taskRepository.existsByIncidentIdAndStaffIdAndStatusIn(incidentId, staffId, StaffService.ACTIVE_TASK)) {
            throw new ApiException("Staff already has an open task on this incident");
        }
        if (staff.getStatus() != StaffStatus.AVAILABLE || !staffService.tryClaim(staff)) {
            throw new ApiException("Staff is not available");
        }

        // links the task to the accepted plan (if any) so it stays traceable
        Recommendation rec = latest(incidentId, RecommendationStatus.ACCEPTED);
        Task task = createTask(incident, new Pick(staff, distance(staff, incident), false), rec, operatorId,
                ActorType.HUMAN, operatorId, "Manual assignment", notes);

        incidentService.changeStatus(incidentId, IncidentStatus.ASSIGNED, ActorType.HUMAN, operatorId);
        return DTOMapper.toDTO(task);
    }

    // ---------------- re-assignment (rejected / not accepted in time) ----------------

    // one replacement with the same specialty, excluding everybody who already had a task on this incident
    @Transactional
    public void reassign(Task rejected, String why) {
        Incident incident = rejected.getIncident();
        if (IncidentService.isClosed(incident.getStatus())) return;

        Staff previous = rejected.getStaff();
        Recommendation rec = rejected.getRecommendation();
        StaffSpecialty specialty = previous.getSpecialty();
        // an assistant (escort or LOW incident) is replaced by an assistant; certified by what the plan required
        StaffLevel level = previous.getLevel() == StaffLevel.ASSISTANT ? StaffLevel.ASSISTANT
                : rec != null && rec.getRequiredLevel() != null ? rec.getRequiredLevel() : StaffLevel.CERTIFIED;

        Set<Long> excluded = taskRepository.findByIncidentId(incident.getId()).stream()
                .map(t -> t.getStaff().getId()).collect(Collectors.toCollection(HashSet::new));

        for (Pick pick : rank(incident, specialty, level, excluded, false)) {
            if (!staffService.tryClaim(pick.staff())) continue;
            createTask(incident, pick, rec, null, ActorType.SYSTEM, null,
                    "Re-assigned after task #" + rejected.getId() + " (" + why + ")", rejected.getNotes());
            return;
        }
        escalate(incident, "No replacement " + specialty + " (" + level + ") for task #" + rejected.getId()
                + " (" + why + ")");
    }

    // ---------------- escalation ----------------

    @Transactional
    public void escalate(Incident incident, String reason) {
        String message = incident.getCode() + ": " + reason;
        notificationService.notifySupervisors(incident.getId(), message, incident.getSeverity());
        activityLogService.log(EntityType.INCIDENT, incident.getId(), incident.getId(), ActionType.ESCALATED,
                ActorType.SYSTEM, null, null, null, reason);
    }

    // ---------------- helpers ----------------

    private Task createTask(Incident incident, Pick pick, Recommendation rec, Long assignedById,
                            ActorType actorType, Long actorId, String logDetails, String notes) {
        Staff staff = pick.staff();
        Task task = new Task();
        task.setIncident(incident);
        task.setStaff(staff);
        task.setRecommendation(rec);
        task.setAssignedBy(assignedById == null ? null : staffRepository.getReferenceById(assignedById));
        task.setStatus(TaskStatus.PENDING);
        task.setDistanceMeters(pick.distance() == null ? null : Math.round(pick.distance() * 10) / 10.0);
        task.setEstimatedArrivalMinutes(eta(pick.distance()));
        task.setAssignedAt(LocalDateTime.now());
        task.setIsEscalated(false);
        if (StringUtils.hasText(notes)) task.setNotes(notes.trim());
        taskRepository.save(task);

        activityLogService.log(EntityType.TASK, task.getId(), incident.getId(), ActionType.ASSIGNED, actorType, actorId,
                null, staff.getName() + " (" + staff.getSpecialty() + "/" + staff.getLevel() + ")",
                logDetails + (pick.distance() == null ? "" : String.format(Locale.ROOT, ", %.0f m away", pick.distance())));

        notificationService.notifyStaff(staff.getId(), incident.getId(), NotificationType.NEW_TASK,
                "New task " + incident.getCode() + ": " + incident.getTitle()
                        + (pick.escort() ? " (escort)" : "")
                        + (task.getEstimatedArrivalMinutes() == null ? "" : " — ETA " + task.getEstimatedArrivalMinutes() + " min"),
                incident.getSeverity());

        broadcastService.send(BroadcastService.TASKS, "TASK_CREATED", DTOMapper.toDTO(task));
        return task;
    }

    // FIELD + AVAILABLE + specialty + on shift, nearest first.
    // CERTIFIED required -> certified only. ASSISTANT required -> assistants first, certified as a fallback.
    private List<Pick> rank(Incident incident, StaffSpecialty specialty, StaffLevel level, Set<Long> excluded, boolean escort) {
        if (specialty == null) return List.of();
        LocalTime now = LocalTime.now();
        List<Staff> pool = staffRepository.findByRoleAndStatusAndSpecialty(StaffRole.FIELD, StaffStatus.AVAILABLE, specialty)
                .stream()
                .filter(s -> !excluded.contains(s.getId()))
                .filter(s -> StaffService.isOnShift(s, now))
                .toList();

        Comparator<Pick> nearest = Comparator.comparing(Pick::distance, Comparator.nullsLast(Comparator.<Double>naturalOrder()));
        Stream<Pick> certified = pool.stream().filter(s -> s.getLevel() == StaffLevel.CERTIFIED)
                .map(s -> new Pick(s, distance(s, incident), escort)).sorted(nearest);
        if (level != StaffLevel.ASSISTANT) return certified.toList();

        Stream<Pick> assistants = pool.stream().filter(s -> s.getLevel() == StaffLevel.ASSISTANT)
                .map(s -> new Pick(s, distance(s, incident), escort)).sorted(nearest);
        return Stream.concat(assistants, certified).toList();
    }

    // what the incident currently needs: latest PENDING/ACCEPTED recommendation, else defaults by category
    private Requirement currentRequirement(Incident incident) {
        Recommendation rec = latest(incident.getId(), RecommendationStatus.PENDING);
        if (rec == null) rec = latest(incident.getId(), RecommendationStatus.ACCEPTED);
        if (rec != null && rec.getRequiredSpecialty() != null) {
            return new Requirement(rec.getRequiredSpecialty(),
                    rec.getRequiredLevel() == null ? StaffLevel.CERTIFIED : rec.getRequiredLevel(),
                    rec.getStaffCount() == null ? 1 : Math.max(1, rec.getStaffCount()));
        }
        return new Requirement(PlaybookService.defaultSpecialty(incident.getCategory()),
                incident.getSeverity() == Severity.LOW ? StaffLevel.ASSISTANT : StaffLevel.CERTIFIED, 1);
    }

    private Recommendation latest(Long incidentId, RecommendationStatus status) {
        return recommendationRepository.findByIncidentIdAndStatus(incidentId, status).stream()
                .max(Comparator.comparing(Recommendation::getId)) // ids grow with time
                .orElse(null);
    }

    private Set<Long> staffOnIncident(Long incidentId) {
        return taskRepository.findByIncidentId(incidentId).stream()
                .map(t -> t.getStaff().getId())
                .collect(Collectors.toCollection(HashSet::new));
    }

    private boolean hasActiveAssistant(Long incidentId, StaffSpecialty specialty) {
        return taskRepository.findByIncidentIdAndStatusNotIn(incidentId,
                        List.of(TaskStatus.DONE, TaskStatus.REJECTED, TaskStatus.CANCELLED)).stream()
                .anyMatch(t -> t.getStaff().getLevel() == StaffLevel.ASSISTANT && t.getStaff().getSpecialty() == specialty);
    }

    private Double distance(Staff staff, Incident incident) {
        double[] pos = StaffService.positionOf(staff);
        if (pos == null || incident.getX() == null || incident.getY() == null) return null;
        return AccessibilityService.distance(pos[0], pos[1], incident.getX(), incident.getY());
    }

    private Integer eta(Double distance) {
        if (distance == null || walkingSpeed <= 0) return null;
        return (int) Math.ceil(distance / walkingSpeed / 60.0);
    }

    private CandidateDTOOut toCandidate(Pick p) {
        return new CandidateDTOOut(DTOMapper.toDTO(p.staff()),
                p.distance() == null ? null : Math.round(p.distance() * 10) / 10.0, eta(p.distance()), p.escort());
    }
}
