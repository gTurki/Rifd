package com.example.rifd.Mapper;

import com.example.rifd.DTO.ActivityLogResponse;
import com.example.rifd.DTO.AlertResponse;
import com.example.rifd.DTO.EventResponse;
import com.example.rifd.DTO.IncidentResponse;
import com.example.rifd.DTO.ZoneResponse;
import com.example.rifd.Entity.ActivityLog;
import com.example.rifd.Entity.Alert;
import com.example.rifd.Entity.BaseEntity;
import com.example.rifd.Entity.Event;
import com.example.rifd.Entity.Incident;
import com.example.rifd.Entity.Zone;

public final class IntakeMapper {

    private IntakeMapper() {
    }

    public static ZoneResponse toResponse(Zone zone) {
        return new ZoneResponse(zone.getId(), zone.getCode(), zone.getName(), zone.getType(),
                zone.getPolygonCoordinates(), zone.getCenterX(), zone.getCenterY(),
                zone.getCapacity(), zone.getCurrentOccupancy(), zone.getCreatedAt(), zone.getUpdatedAt());
    }

    public static EventResponse toResponse(Event event) {
        return new EventResponse(event.getId(), event.getName(), event.getType(), idOf(event.getZone()),
                event.getStartTime(), event.getEndTime(), event.getExpectedAttendance(),
                event.getCreatedAt(), event.getUpdatedAt());
    }

    public static AlertResponse toResponse(Alert alert) {
        return new AlertResponse(alert.getId(), alert.getSource(), alert.getSourceReference(),
                alert.getRawText(), alert.getLanguage(), alert.getAttachmentUrl(), alert.getX(), alert.getY(),
                idOf(alert.getZone()), alert.getReceivedAt(), alert.getProcessedAt(), alert.getAiSeverity(),
                alert.getAiCategory(), alert.getAiConfidence(), alert.getStatus(), idOf(alert.getIncident()),
                alert.getMergeScore(), alert.getCreatedAt(), alert.getUpdatedAt());
    }

    public static IncidentResponse toResponse(Incident incident) {
        return new IncidentResponse(incident.getId(), incident.getCode(), incident.getTitle(),
                incident.getSummary(), incident.getCategory(), incident.getSeverity(), incident.getStatus(),
                idOf(incident.getZone()), idOf(incident.getEvent()), incident.getX(), incident.getY(),
                incident.getAlertCount(), incident.getAffectsAccessibility(), idOf(incident.getAccessibilityAsset()),
                incident.getAcknowledgedAt(), incident.getResolvedAt(), incident.getResolutionNotes(),
                incident.getActualSeverity(), incident.getCreatedAt(), incident.getUpdatedAt());
    }

    public static ActivityLogResponse toResponse(ActivityLog log) {
        return new ActivityLogResponse(log.getId(), log.getEntityType(), log.getEntityId(),
                idOf(log.getIncident()), log.getAction(), log.getActorType(), idOf(log.getActor()),
                log.getOldValue(), log.getNewValue(), log.getDetails(), log.getTimestamp(),
                log.getCreatedAt(), log.getUpdatedAt());
    }

    private static Long idOf(BaseEntity entity) {
        return entity == null ? null : entity.getId();
    }
}
