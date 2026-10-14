package com.example.rifd.Repository;

import com.example.rifd.Entity.ActivityLog;
import com.example.rifd.Enum.ActionType;
import com.example.rifd.Enum.ActorType;
import org.springframework.data.domain.Pageable;
import com.example.rifd.Enum.EntityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

import java.util.List;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    // incident timeline, oldest first
    List<ActivityLog> findByIncidentIdOrderByTimestampAscIdAsc(Long incidentId);

    // latest N across the system
    List<ActivityLog> findAllByOrderByTimestampDescIdDesc(Pageable pageable);

    // used to detect a manual severity override (human decision wins)
    boolean existsByIncidentIdAndActionAndActorType(Long incidentId, ActionType action, ActorType actorType);

    // stats: aiDecisions / fallbackDecisions = (ALERT, CREATED, AI|SYSTEM)
    long countByEntityTypeAndActionAndActorType(EntityType entityType, ActionType action, ActorType actorType);

    // latest status change of an asset (who took it out of service: a human or the system)
    Optional<ActivityLog> findFirstByEntityTypeAndEntityIdAndActionOrderByIdDesc(EntityType entityType, Long entityId,
                                                                                 ActionType action);
}
