package com.example.rifd.Service;

import com.example.rifd.Api.ApiException;
import com.example.rifd.DTOOut.IncidentDTOOut;
import com.example.rifd.DTOOut.IncidentDetailsDTOOut;
import com.example.rifd.Entity.Alert;
import com.example.rifd.Entity.Event;
import com.example.rifd.Entity.Incident;
import com.example.rifd.Entity.Zone;
import com.example.rifd.Enum.ActionType;
import com.example.rifd.Enum.ActorType;
import com.example.rifd.Enum.AlertStatus;
import com.example.rifd.Enum.EntityType;
import com.example.rifd.Enum.IncidentCategory;
import com.example.rifd.Enum.IncidentStatus;
import com.example.rifd.Enum.Severity;
import com.example.rifd.Events.IncidentAssetLinkedEvent;
import com.example.rifd.Events.IncidentCreatedEvent;
import com.example.rifd.Events.IncidentResolvedEvent;
import com.example.rifd.Events.IncidentSeverityChangedEvent;
import com.example.rifd.Mapper.DTOMapper;
import com.example.rifd.Repository.AlertRepository;
import com.example.rifd.Repository.IncidentRepository;
import com.example.rifd.Repository.StaffRepository;
import com.example.rifd.Repository.TaskRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class IncidentService {

    // HIGH first, then newest first
    private static final Comparator<Incident> BY_PRIORITY =
            Comparator.comparingInt((Incident i) -> i.getSeverity() == null ? -1 : i.getSeverity().ordinal())
                    .reversed()
                    .thenComparing(Incident::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));

    // a live event nearby raises these categories one level
    private static final Set<IncidentCategory> EVENT_SENSITIVE = EnumSet.of(
            IncidentCategory.CROWD, IncidentCategory.MEDICAL, IncidentCategory.SECURITY, IncidentCategory.FIRE);

    private static final Set<IncidentCategory> ACCESSIBILITY_RELATED = EnumSet.of(
            IncidentCategory.ACCESSIBILITY, IncidentCategory.TECHNICAL_FAILURE);

    private static final int TITLE_MAX = 80;

    private static final List<String> ASSET_WORDS = List.of("مصعد", "منحدر", "كرسي", "مدخل", "دوره مياه",
            "elevator", "lift", "ramp", "wheelchair", "entrance", "toilet", "shuttle");

    private final IncidentRepository incidentRepository;
    private final AlertRepository alertRepository;
    private final StaffRepository staffRepository;
    private final ActivityLogService activityLogService;
    private final BroadcastService broadcastService;
    private final EventService eventService;
    private final AccessibilityService accessibilityService;
    private final ApplicationEventPublisher eventPublisher;
    private final DashboardService dashboardService;
    private final EntityManager entityManager;
    private final TaskRepository taskRepository;

    @Value("${event.severity-boost-radius:100}")
    private double eventBoostRadius;

    @Value("${accessibility.asset-match-radius:40}")
    private double assetMatchRadius;

    // ---------------- read ----------------

    @Transactional(readOnly = true)
    public Incident getIncident(Long incidentId) {
        return incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ApiException("Incident not found"));
    }

    // SELECT ... FOR UPDATE + fresh state: the pipeline and the operator never overwrite each other
    @Transactional
    public Incident getIncidentForUpdate(Long incidentId) {
        Incident incident = entityManager.find(Incident.class, incidentId);
        if (incident == null) throw new ApiException("Incident not found");
        // already locked in this transaction -> keep it (a refresh would drop unsaved changes)
        if (entityManager.getLockMode(incident) != LockModeType.PESSIMISTIC_WRITE) {
            entityManager.refresh(incident, LockModeType.PESSIMISTIC_WRITE);
        }
        return incident;
    }

    @Transactional(readOnly = true)
    public List<IncidentDTOOut> search(IncidentStatus status, Severity severity, IncidentCategory category) {
        return incidentRepository.search(status, severity, category).stream()
                .sorted(BY_PRIORITY)
                .map(DTOMapper::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public IncidentDetailsDTOOut getDetails(Long incidentId) {
        Incident incident = getIncident(incidentId);
        return DTOMapper.toDTO(incident, alertRepository.findByIncidentIdOrderByReceivedAtAsc(incidentId));
    }

    // ---------------- alert pipeline (called by AlertService inside its transaction) ----------------

    @Transactional
    public Incident createFromAlert(Alert alert, String aiSummary, ActorType decidedBy) {
        Incident incident = new Incident();
        // count + 1 is safe: AlertService runs merge/create one alert at a time; restarts at 0001 after reset
        incident.setCode(String.format(Locale.ROOT, "INC-%04d", incidentRepository.count() + 1));

        String summary = StringUtils.hasText(aiSummary) ? aiSummary.trim() : alert.getRawText();
        incident.setTitle(truncate(summary, TITLE_MAX));
        incident.setSummary(summary);
        incident.setCategory(alert.getAiCategory());
        incident.setStatus(IncidentStatus.OPEN);
        incident.setZone(alert.getZone());
        incident.setX(alert.getX());
        incident.setY(alert.getY());
        incident.setAlertCount(1);

        Severity baseSeverity = alert.getAiSeverity();
        Optional<Event> event = findBoostingEvent(incident);
        event.ifPresent(incident::setEvent);
        Severity severity = event.isPresent() ? raise(baseSeverity) : baseSeverity;
        incident.setSeverity(severity);

        String assetNote = linkAccessibilityAsset(incident, alert.getRawText());
        incidentRepository.save(incident);
        alert.setIncident(incident);

        Long id = incident.getId();
        activityLogService.log(EntityType.INCIDENT, id, id, ActionType.CREATED, decidedBy, null,
                null, String.valueOf(baseSeverity),
                "Created from alert #" + alert.getId() + " (" + incident.getCategory() + ")" + assetNote);

        if (severity != baseSeverity) {
            activityLogService.log(EntityType.INCIDENT, id, id, ActionType.SEVERITY_CHANGED, ActorType.SYSTEM, null,
                    String.valueOf(baseSeverity), String.valueOf(severity),
                    "Raised one level: live event '" + event.get().getName() + "' nearby");
        }

        eventPublisher.publishEvent(new IncidentCreatedEvent(id));
        if (incident.getAccessibilityAsset() != null) {
            eventPublisher.publishEvent(new IncidentAssetLinkedEvent(id, incident.getAccessibilityAsset().getId()));
        }
        broadcastService.send(BroadcastService.INCIDENTS, "INCIDENT_CREATED", DTOMapper.toDTO(incident));
        return incident;
    }

    @Transactional
    public Incident mergeAlert(Incident incident, Alert alert, double score, ActorType decidedBy) {
        Long id = incident.getId();
        int previousCount = incident.getAlertCount() == null ? 0 : incident.getAlertCount();
        int count = previousCount + 1;

        updateAveragePosition(incident, alert, count);
        incident.setAlertCount(count);

        alert.setIncident(incident);
        alert.setStatus(AlertStatus.MERGED);
        alert.setMergeScore(score);

        activityLogService.log(EntityType.INCIDENT, id, id, ActionType.MERGED, decidedBy, null,
                String.valueOf(previousCount), String.valueOf(count),
                String.format(Locale.ROOT, "Alert #%d merged, score %.2f", alert.getId(), score));

        // the first alert may have been too far from the asset; a later (closer) alert can still link it
        if (incident.getAccessibilityAsset() == null) {
            String assetNote = linkAccessibilityAsset(incident, alert.getRawText());            if (incident.getAccessibilityAsset() != null) {
                activityLogService.log(EntityType.INCIDENT, id, id, ActionType.STATUS_CHANGED, ActorType.SYSTEM, null,
                        "affectsAccessibility=false", "affectsAccessibility=true",
                        "Accessibility impact detected on merge" + assetNote);
                eventPublisher.publishEvent(new IncidentAssetLinkedEvent(id, incident.getAccessibilityAsset().getId()));
            }
        }

        Optional<Event> event = findBoostingEvent(incident);
        if (event.isPresent() && incident.getEvent() == null) {
            incident.setEvent(event.get());
        }

        // a manual severity change by an operator wins: no automatic raise after it
        if (!activityLogService.hasHumanSeverityOverride(id)) {
            Severity oldSeverity = incident.getSeverity();
            Severity candidate = alert.getAiSeverity();
            String reason = "Highest severity among merged alerts";

            if (event.isPresent()) {
                candidate = raise(candidate);
                reason += " (raised one level: live event '" + event.get().getName() + "' nearby)";
            }

            if (candidate != null && (oldSeverity == null || candidate.ordinal() > oldSeverity.ordinal())) {
                incident.setSeverity(candidate);
                activityLogService.log(EntityType.INCIDENT, id, id, ActionType.SEVERITY_CHANGED, ActorType.SYSTEM,
                        null, String.valueOf(oldSeverity), candidate.name(), reason);
                eventPublisher.publishEvent(new IncidentSeverityChangedEvent(id, oldSeverity, candidate));
            }
        }

        incidentRepository.save(incident);
        broadcastIncident(incident);
        return incident;
    }

    // an alert was taken out of this incident by the operator
    @Transactional
    public void detachAlert(Long incidentId, Long alertId, String reason, Long actorId) {
        Incident incident = getIncidentForUpdate(incidentId);
        int before = incident.getAlertCount() == null ? 0 : incident.getAlertCount();
        int after = Math.max(0, before - 1);
        incident.setAlertCount(after);
        incidentRepository.save(incident);
        activityLogService.log(EntityType.INCIDENT, incidentId, incidentId, ActionType.STATUS_CHANGED, ActorType.HUMAN,
                actorId, String.valueOf(before), String.valueOf(after),
                "Alert #" + alertId + " removed: " + reason + (after == 0 ? " (no alerts left: consider false alarm)" : ""));
        broadcastIncident(incident);
    }

    // ---------------- operator actions ----------------

    @Transactional
    public IncidentDTOOut acknowledge(Long incidentId, Long actorStaffId) {
        validateActor(actorStaffId);
        Incident incident = getOpenIncident(incidentId);

        if (incident.getAcknowledgedAt() == null) { // idempotent
            incident.setAcknowledgedAt(LocalDateTime.now());
            incidentRepository.save(incident);
            activityLogService.log(EntityType.INCIDENT, incidentId, incidentId, ActionType.ACKNOWLEDGED,
                    ActorType.HUMAN, actorStaffId, null, null, "Operator acknowledged the incident");
            broadcastIncident(incident);
        }
        return DTOMapper.toDTO(incident);
    }

    @Transactional
    public IncidentDTOOut updateSeverity(Long incidentId, Severity newSeverity, String reason, Long actorStaffId) {
        validateActor(actorStaffId);
        Incident incident = getOpenIncident(incidentId);
        Severity oldSeverity = incident.getSeverity();

        if (newSeverity == oldSeverity) return DTOMapper.toDTO(incident);

        incident.setSeverity(newSeverity);
        incidentRepository.save(incident);

        activityLogService.log(EntityType.INCIDENT, incidentId, incidentId, ActionType.SEVERITY_CHANGED,
                ActorType.HUMAN, actorStaffId, String.valueOf(oldSeverity), newSeverity.name(),
                StringUtils.hasText(reason) ? "Manual override: " + reason.trim() : "Manual override");

        eventPublisher.publishEvent(new IncidentSeverityChangedEvent(incidentId, oldSeverity, newSeverity));
        broadcastIncident(incident);
        dashboardService.broadcastStats();
        return DTOMapper.toDTO(incident);
    }

    // backward / late transitions are ignored quietly; the response shows the actual status
    @Transactional
    public IncidentDTOOut updateStatus(Long incidentId, IncidentStatus newStatus, Long actorStaffId) {
        // closing goes through the same path as /resolve and /false-alarm (actualSeverity, notes, 400 if closed)
        if (newStatus == IncidentStatus.RESOLVED) return resolve(incidentId, null, null, actorStaffId);
        if (newStatus == IncidentStatus.FALSE_ALARM) return markFalseAlarm(incidentId, null, actorStaffId);

        validateActor(actorStaffId);
        changeStatus(incidentId, newStatus, ActorType.HUMAN, actorStaffId);
        return DTOMapper.toDTO(getIncident(incidentId));
    }

    @Transactional
    public IncidentDTOOut resolve(Long incidentId, String resolutionNotes, Severity actualSeverity, Long actorStaffId) {
        validateActor(actorStaffId);
        Incident incident = getOpenIncident(incidentId);

        if (StringUtils.hasText(resolutionNotes)) incident.setResolutionNotes(resolutionNotes.trim());
        incident.setActualSeverity(actualSeverity != null ? actualSeverity : incident.getSeverity());

        changeStatus(incidentId, IncidentStatus.RESOLVED, ActorType.HUMAN, actorStaffId);
        return DTOMapper.toDTO(incident);
    }

    @Transactional
    public IncidentDTOOut markFalseAlarm(Long incidentId, String notes, Long actorStaffId) {
        validateActor(actorStaffId);
        Incident incident = getOpenIncident(incidentId);

        if (StringUtils.hasText(notes)) incident.setResolutionNotes(notes.trim());

        changeStatus(incidentId, IncidentStatus.FALSE_ALARM, ActorType.HUMAN, actorStaffId);
        return DTOMapper.toDTO(incident);
    }

    // ---------------- status ----------------

    @Transactional
    public IncidentStatus changeStatus(Long incidentId, IncidentStatus newStatus,
                                       ActorType actorType, Long actorStaffId) {
        Incident incident = getIncidentForUpdate(incidentId);
        IncidentStatus current = incident.getStatus();

        if (!isAllowedTransition(current, newStatus)) {
            return current; // late or backward request: ignore quietly
        }

        incident.setStatus(newStatus);
        if (isClosed(newStatus)) {
            incident.setResolvedAt(LocalDateTime.now());
        }
        incidentRepository.save(incident);

        ActionType action = newStatus == IncidentStatus.RESOLVED ? ActionType.RESOLVED : ActionType.STATUS_CHANGED;
        activityLogService.log(EntityType.INCIDENT, incidentId, incidentId, action, actorType, actorStaffId,
                current.name(), newStatus.name(), null);

        if (isClosed(newStatus)) {
            eventPublisher.publishEvent(new IncidentResolvedEvent(incidentId, newStatus));
        }

        broadcastIncident(incident);
        if (isClosed(newStatus)) dashboardService.broadcastStats(); // open incidents changed
        return newStatus;
    }

    // a rejected / timed-out / cancelled task left nobody on it: ASSIGNED -> OPEN
    // (IN_PROGRESS stays: somebody already started working on it)
    @Transactional
    public void reopenIfUnstaffed(Long incidentId) {
        Incident incident = getIncidentForUpdate(incidentId);
        if (incident.getStatus() != IncidentStatus.ASSIGNED) return;
        if (taskRepository.countByIncidentIdAndStatusIn(incidentId, StaffService.ACTIVE_TASK) > 0) return;

        incident.setStatus(IncidentStatus.OPEN);
        incidentRepository.save(incident);
        activityLogService.log(EntityType.INCIDENT, incidentId, incidentId, ActionType.STATUS_CHANGED, ActorType.SYSTEM,
                null, IncidentStatus.ASSIGNED.name(), IncidentStatus.OPEN.name(), "No active task left on the incident");
        broadcastIncident(incident);
    }

    public void broadcastIncident(Incident incident) {
        broadcastService.send(BroadcastService.INCIDENTS, "INCIDENT_UPDATED", DTOMapper.toDTO(incident));
    }

    public static boolean isClosed(IncidentStatus status) {
        return status == IncidentStatus.RESOLVED || status == IncidentStatus.FALSE_ALARM;
    }

    // one level up, capped at HIGH
    public static Severity raise(Severity severity) {
        if (severity == null) return null;
        Severity[] values = Severity.values();
        return values[Math.min(severity.ordinal() + 1, values.length - 1)];
    }

    // ---------------- helpers ----------------

    public Incident getOpenIncident(Long incidentId) {
        Incident incident = getIncidentForUpdate(incidentId);
        if (isClosed(incident.getStatus())) {
            throw new ApiException("Incident is already closed");
        }
        return incident;
    }

    private static boolean mentionsAsset(String text) {
        String t = RuleBasedClassifier.normalize(text);
        return ASSET_WORDS.stream().map(RuleBasedClassifier::normalize).anyMatch(t::contains);
    }

    // before Security: actorStaffId comes from the request and is optional
    public void validateActor(Long actorStaffId) {
        if (actorStaffId != null && !staffRepository.existsById(actorStaffId)) {
            throw new ApiException("Staff not found");
        }
    }

    private boolean isAllowedTransition(IncidentStatus current, IncidentStatus next) {
        if (next == null || isClosed(current)) return false;
        if (next == IncidentStatus.FALSE_ALARM) return true;
        return next.ordinal() > current.ordinal();
    }

    private Optional<Event> findBoostingEvent(Incident incident) {
        if (!EVENT_SENSITIVE.contains(incident.getCategory())) return Optional.empty();
        Long zoneId = incident.getZone() == null ? null : incident.getZone().getId();

        return eventService.getActiveEvents().stream()
                .filter(e -> e.getZone() != null)
                .filter(e -> (zoneId != null && Objects.equals(e.getZone().getId(), zoneId))
                        || isWithinRadius(e.getZone(), incident.getX(), incident.getY()))
                .findFirst();
    }

    private boolean isWithinRadius(Zone zone, Double x, Double y) {
        return x != null && y != null && zone.getCenterX() != null && zone.getCenterY() != null
                && AccessibilityService.distance(zone.getCenterX(), zone.getCenterY(), x, y) <= eventBoostRadius;
    }

    private String linkAccessibilityAsset(Incident incident, String alertText) {
        if (!ACCESSIBILITY_RELATED.contains(incident.getCategory())) return "";
        // a generic technical failure (screen, power...) must not take a nearby ramp out of service
        if (incident.getCategory() == IncidentCategory.TECHNICAL_FAILURE && !mentionsAsset(alertText)) return "";
        Long zoneId = incident.getZone() == null ? null : incident.getZone().getId();

        return accessibilityService.findNearestAsset(incident.getX(), incident.getY(), zoneId, assetMatchRadius)
                .map(asset -> {
                    incident.setAffectsAccessibility(true);
                    incident.setAccessibilityAsset(asset);
                    return " | linked asset: " + asset.getName();
                })
                .orElse("");
    }

    private void updateAveragePosition(Incident incident, Alert alert, int newCount) {
        if (alert.getX() == null || alert.getY() == null) return;
        if (incident.getX() == null || incident.getY() == null || newCount <= 1) {
            incident.setX(alert.getX());
            incident.setY(alert.getY());
            return;
        }
        incident.setX((incident.getX() * (newCount - 1) + alert.getX()) / newCount);
        incident.setY((incident.getY() * (newCount - 1) + alert.getY()) / newCount);
    }

    private static String truncate(String text, int max) {
        if (text == null) return null;
        String t = text.trim();
        return t.length() <= max ? t : t.substring(0, max - 1).trim() + "…";
    }


}