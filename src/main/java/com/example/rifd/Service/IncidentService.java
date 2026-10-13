package com.example.rifd.Service;

import com.example.rifd.Api.ApiException;
import com.example.rifd.DTOOut.IncidentDTOOut;
import com.example.rifd.DTOOut.IncidentDetailsDTOOut;
import com.example.rifd.Entity.Incident;
import com.example.rifd.Enum.ActionType;
import com.example.rifd.Enum.ActorType;
import com.example.rifd.Enum.EntityType;
import com.example.rifd.Enum.IncidentCategory;
import com.example.rifd.Enum.IncidentStatus;
import com.example.rifd.Enum.Severity;
import com.example.rifd.Events.IncidentResolvedEvent;
import com.example.rifd.Mapper.DTOMapper;
import com.example.rifd.Repository.AlertRepository;
import com.example.rifd.Repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IncidentService {

    // HIGH first, then newest first
    private static final Comparator<Incident> BY_PRIORITY =
            Comparator.comparingInt((Incident i) -> i.getSeverity() == null ? -1 : i.getSeverity().ordinal())
                    .reversed()
                    .thenComparing(Incident::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));

    private final IncidentRepository incidentRepository;
    private final AlertRepository alertRepository;
    private final ActivityLogService activityLogService;
    private final BroadcastService broadcastService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public Incident getIncident(Long incidentId) {
        return incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ApiException("Incident not found"));
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

    @Transactional
    public IncidentStatus changeStatus(Long incidentId, IncidentStatus newStatus,
                                       ActorType actorType, Long actorStaffId) {
        Incident incident = getIncident(incidentId);
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
        return newStatus;
    }

    public void broadcastIncident(Incident incident) {
        broadcastService.send(BroadcastService.INCIDENTS, "INCIDENT_UPDATED", DTOMapper.toDTO(incident));
    }

    public static boolean isClosed(IncidentStatus status) {
        return status == IncidentStatus.RESOLVED || status == IncidentStatus.FALSE_ALARM;
    }

    private boolean isAllowedTransition(IncidentStatus current, IncidentStatus next) {
        if (next == null || isClosed(current)) return false;
        if (next == IncidentStatus.FALSE_ALARM) return true;
        return next.ordinal() > current.ordinal();
    }
}