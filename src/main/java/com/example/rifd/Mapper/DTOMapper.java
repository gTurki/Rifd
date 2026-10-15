package com.example.rifd.Mapper;

import com.example.rifd.DTOOut.ActivityLogDTOOut;
import com.example.rifd.DTOOut.AlertDTOOut;
import com.example.rifd.DTOOut.EventDTOOut;
import com.example.rifd.DTOOut.IncidentDTOOut;
import com.example.rifd.DTOOut.ZoneDTOOut;
import com.example.rifd.Entity.ActivityLog;
import com.example.rifd.Entity.Alert;
import com.example.rifd.Entity.BaseEntity;
import com.example.rifd.Entity.Event;
import com.example.rifd.Entity.Incident;
import com.example.rifd.Entity.Zone;
import com.example.rifd.DTOOut.IncidentDetailsDTOOut;
import com.example.rifd.DTOOut.AccessibilityAssetDTOOut;
import com.example.rifd.DTOOut.AccessibleRouteDTOOut;
import com.example.rifd.DTOOut.NotificationDTOOut;
import com.example.rifd.DTOOut.PlaybookDTOOut;
import com.example.rifd.DTOOut.RecommendationDTOOut;
import com.example.rifd.DTOOut.StaffDTOOut;
import com.example.rifd.DTOOut.TaskDTOOut;
import com.example.rifd.Entity.AccessibilityAsset;
import com.example.rifd.Entity.AccessibleRoute;
import com.example.rifd.Entity.Notification;
import com.example.rifd.Entity.Playbook;
import com.example.rifd.Entity.Recommendation;
import com.example.rifd.Entity.Staff;
import com.example.rifd.Entity.Task;

import java.util.List;

public final class DTOMapper {

    private DTOMapper() {
    }

    public static ZoneDTOOut toDTO(Zone zone) {
        return new ZoneDTOOut(zone.getId(), zone.getCode(), zone.getName(), zone.getType(),
                zone.getPolygonCoordinates(), zone.getCenterX(), zone.getCenterY(),
                zone.getCapacity(), zone.getCurrentOccupancy(), zone.getCreatedAt(), zone.getUpdatedAt());
    }

    public static EventDTOOut toDTO(Event event) {
        return new EventDTOOut(event.getId(), event.getName(), event.getType(), idOf(event.getZone()),
                event.getStartTime(), event.getEndTime(), event.getExpectedAttendance(),
                event.getCreatedAt(), event.getUpdatedAt());
    }

    public static AlertDTOOut toDTO(Alert alert) {
        return new AlertDTOOut(alert.getId(), alert.getSource(), alert.getSourceReference(),
                alert.getRawText(), alert.getLanguage(), alert.getAttachmentUrl(), alert.getX(), alert.getY(),
                idOf(alert.getZone()), alert.getReceivedAt(), alert.getProcessedAt(), alert.getAiSeverity(),
                alert.getAiCategory(), alert.getAiConfidence(), alert.getStatus(), idOf(alert.getIncident()),
                alert.getMergeScore(), alert.getCreatedAt(), alert.getUpdatedAt(),
                idOf(alert.getReporter()), alert.getReporterTrust());
    }

    public static IncidentDTOOut toDTO(Incident incident) {
        return new IncidentDTOOut(incident.getId(), incident.getCode(), incident.getTitle(),
                incident.getSummary(), incident.getCategory(), incident.getSeverity(), incident.getStatus(),
                idOf(incident.getZone()), idOf(incident.getEvent()), incident.getX(), incident.getY(),
                incident.getAlertCount(), incident.getAffectsAccessibility(), idOf(incident.getAccessibilityAsset()),
                incident.getAcknowledgedAt(), incident.getResolvedAt(), incident.getResolutionNotes(),
                incident.getActualSeverity(), incident.getCreatedAt(), incident.getUpdatedAt(),
                idOf(incident.getPossibleDuplicateOf()), incident.getPossibleDuplicateOfCode(),
                incident.getDuplicateScore());
    }

    public static IncidentDetailsDTOOut toDTO(Incident incident, List<Alert> alerts) {
        return new IncidentDetailsDTOOut(toDTO(incident),
                alerts.stream().map(DTOMapper::toDTO).toList());
    }

    public static ActivityLogDTOOut toDTO(ActivityLog log) {
        return new ActivityLogDTOOut(log.getId(), log.getEntityType(), log.getEntityId(),
                idOf(log.getIncident()), log.getAction(), log.getActorType(), idOf(log.getActor()),
                log.getOldValue(), log.getNewValue(), log.getDetails(), log.getTimestamp(),
                log.getCreatedAt(), log.getUpdatedAt());
    }

    public static StaffDTOOut toDTO(Staff staff) {
        return new StaffDTOOut(staff.getId(), staff.getEmployeeNumber(), staff.getName(), staff.getPhone(), staff.getEmail(),
                staff.getRole(), staff.getSpecialty(), staff.getLevel(), staff.getStatus(), idOf(staff.getHomeZone()),
                staff.getCurrentX(), staff.getCurrentY(), staff.getLastLocationUpdate(),
                staff.getShiftStart(), staff.getShiftEnd(), staff.getUsername(),
                staff.getCreatedAt(), staff.getUpdatedAt());
    }

    public static PlaybookDTOOut toDTO(Playbook playbook) {
        return new PlaybookDTOOut(playbook.getId(), playbook.getTitle(), playbook.getCategory(),
                playbook.getMinSeverity(), playbook.getSteps(), playbook.getRequiredSpecialty(),
                playbook.getRequiredLevel(), playbook.getRecommendedStaffCount(), playbook.getTargetResponseMinutes(),
                playbook.getIsActive(), playbook.getCreatedAt(), playbook.getUpdatedAt());
    }

    // needs an open session (playbook title is lazy)
    public static RecommendationDTOOut toDTO(Recommendation r) {
        return new RecommendationDTOOut(r.getId(), idOf(r.getIncident()), idOf(r.getPlaybook()),
                r.getPlaybook() == null ? null : r.getPlaybook().getTitle(),
                r.getActionText(), r.getReasoning(), r.getRequiredSpecialty(), r.getRequiredLevel(),
                r.getStaffCount(), r.getConfidence(), r.getStatus(), r.getRejectionReason(),
                idOf(r.getDecidedBy()), r.getDecidedAt(), r.getCreatedAt(), r.getUpdatedAt());
    }

    // needs an open session (incident code / staff name are lazy)
    public static TaskDTOOut toDTO(Task t) {
        Staff staff = t.getStaff();
        Incident i = t.getIncident();
        return new TaskDTOOut(t.getId(), idOf(i),
                i == null ? null : i.getCode(),
                i == null ? null : i.getTitle(),
                i == null ? null : i.getSeverity(),
                i == null ? null : i.getX(),
                i == null ? null : i.getY(),
                i == null || i.getZone() == null ? null : i.getZone().getName(),
                idOf(staff), staff == null ? null : staff.getName(),
                staff == null ? null : staff.getSpecialty(), staff == null ? null : staff.getLevel(),
                idOf(t.getRecommendation()), idOf(t.getAssignedBy()), t.getStatus(),
                t.getDistanceMeters(), t.getEstimatedArrivalMinutes(), t.getAssignedAt(), t.getAcceptedAt(),
                t.getArrivedAt(), t.getCompletedAt(), t.getRejectionReason(), t.getIsEscalated(), t.getNotes(),
                t.getCreatedAt(), t.getUpdatedAt());
    }

    public static NotificationDTOOut toDTO(Notification n) {
        return new NotificationDTOOut(n.getId(), idOf(n.getIncident()), n.getType(), idOf(n.getRecipientStaff()),
                n.getRecipientSpecialty(), n.getMessage(), n.getPriority(), n.getSentAt(), n.getReadAt());
    }

    public static AccessibilityAssetDTOOut toDTO(AccessibilityAsset a) {
        return new AccessibilityAssetDTOOut(a.getId(), a.getName(), a.getType(), idOf(a.getZone()), a.getX(), a.getY(),
                a.getStatus(), a.getLastStatusChange(), a.getExpectedRestoreTime(), a.getCreatedAt(), a.getUpdatedAt());
    }

    // needs an open session (assets is a lazy collection)
    public static AccessibleRouteDTOOut toDTO(AccessibleRoute r) {
        return new AccessibleRouteDTOOut(r.getId(), r.getName(), idOf(r.getFromZone()), idOf(r.getToZone()),
                r.getPathCoordinates(), r.getLengthMeters(), r.getEstimatedMinutes(), r.getStatus(),
                r.getAssets().stream().map(AccessibilityAsset::getId).toList(),
                idOf(r.getAlternativeRoute()), r.getPublicMessageAr(), r.getPublicMessageEn(), r.getLastUpdated(),
                r.getCreatedAt(), r.getUpdatedAt(), r.getPublishedAt());
    }

    private static Long idOf(BaseEntity entity) {
        return entity == null ? null : entity.getId();
    }
}