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
                alert.getMergeScore(), alert.getCreatedAt(), alert.getUpdatedAt());
    }

    public static IncidentDTOOut toDTO(Incident incident) {
        return new IncidentDTOOut(incident.getId(), incident.getCode(), incident.getTitle(),
                incident.getSummary(), incident.getCategory(), incident.getSeverity(), incident.getStatus(),
                idOf(incident.getZone()), idOf(incident.getEvent()), incident.getX(), incident.getY(),
                incident.getAlertCount(), incident.getAffectsAccessibility(), idOf(incident.getAccessibilityAsset()),
                incident.getAcknowledgedAt(), incident.getResolvedAt(), incident.getResolutionNotes(),
                incident.getActualSeverity(), incident.getCreatedAt(), incident.getUpdatedAt());
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

    private static Long idOf(BaseEntity entity) {
        return entity == null ? null : entity.getId();
    }
}