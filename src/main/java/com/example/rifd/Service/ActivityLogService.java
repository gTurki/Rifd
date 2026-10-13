package com.example.rifd.Service;

import com.example.rifd.Entity.ActivityLog;
import com.example.rifd.Enum.ActionType;
import com.example.rifd.Enum.ActorType;
import com.example.rifd.Enum.EntityType;
import com.example.rifd.Repository.ActivityLogRepository;
import com.example.rifd.Repository.IncidentRepository;
import com.example.rifd.Repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;
    private final IncidentRepository incidentRepository;
    private final StaffRepository staffRepository;

    @Transactional
    public void log(EntityType entityType, Long entityId, Long incidentId,
                    ActionType action, ActorType actorType, Long actorStaffId,
                    String oldValue, String newValue, String details) {

        ActivityLog log = new ActivityLog();
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setIncident(incidentId == null ? null : incidentRepository.getReferenceById(incidentId));
        log.setAction(action);
        log.setActorType(actorType);
        // actor only for HUMAN decisions; AI/SYSTEM stay null
        log.setActor(actorType == ActorType.HUMAN && actorStaffId != null
                ? staffRepository.getReferenceById(actorStaffId) : null);
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        log.setDetails(details);
        log.setTimestamp(LocalDateTime.now());

        activityLogRepository.save(log);
    }
}