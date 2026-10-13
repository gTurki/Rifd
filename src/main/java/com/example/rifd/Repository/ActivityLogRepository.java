package com.example.rifd.Repository;

import com.example.rifd.Entity.ActivityLog;
import com.example.rifd.Enum.ActionType;
import com.example.rifd.Enum.ActorType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    // incident timeline, oldest first
    List<ActivityLog> findByIncidentIdOrderByTimestampAscIdAsc(Long incidentId);

    // latest N across the system
    List<ActivityLog> findAllByOrderByTimestampDescIdDesc(Pageable pageable);

    // used to detect a manual severity override (human decision wins)
    boolean existsByIncidentIdAndActionAndActorType(Long incidentId, ActionType action, ActorType actorType);
}