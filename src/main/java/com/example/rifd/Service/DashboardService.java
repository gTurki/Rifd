package com.example.rifd.Service;

import com.example.rifd.DTOOut.DashboardStatsDTOOut;
import com.example.rifd.Enum.ActionType;
import com.example.rifd.Enum.ActorType;
import com.example.rifd.Enum.AlertStatus;
import com.example.rifd.Enum.EntityType;
import com.example.rifd.Enum.IncidentStatus;
import com.example.rifd.Enum.Severity;
import com.example.rifd.Repository.ActivityLogRepository;
import com.example.rifd.Repository.AlertRepository;
import com.example.rifd.Repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final List<IncidentStatus> CLOSED = List.of(IncidentStatus.RESOLVED, IncidentStatus.FALSE_ALARM);

    private final AlertRepository alertRepository;
    private final IncidentRepository incidentRepository;
    private final ActivityLogRepository activityLogRepository;
    private final BroadcastService broadcastService;

    @Transactional(readOnly = true)
    public DashboardStatsDTOOut getStats() {
        // HIGH, MEDIUM, LOW in that order, every level present
        Map<Severity, Long> bySeverity = new LinkedHashMap<>();
        for (int i = Severity.values().length - 1; i >= 0; i--) {
            Severity s = Severity.values()[i];
            bySeverity.put(s, incidentRepository.countBySeverityAndStatusNotIn(s, CLOSED));
        }
        return new DashboardStatsDTOOut(
                alertRepository.count(),
                alertRepository.countByStatus(AlertStatus.MERGED),
                incidentRepository.count(),
                incidentRepository.countByStatusNotIn(CLOSED),
                bySeverity,
                activityLogRepository.countByEntityTypeAndActionAndActorType(EntityType.ALERT, ActionType.CREATED, ActorType.AI),
                activityLogRepository.countByEntityTypeAndActionAndActorType(EntityType.ALERT, ActionType.CREATED, ActorType.SYSTEM));
    }

    // called inside the caller's transaction: counts include its own changes, the message leaves after commit
    @Transactional
    public void broadcastStats() {
        broadcastService.send(BroadcastService.STATS, "STATS_UPDATED", getStats());
    }
}
