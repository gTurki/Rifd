package com.example.rifd.Repository;

import com.example.rifd.Entity.Alert;
import com.example.rifd.Enum.AlertStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    @Query("""
           SELECT a FROM Alert a
           LEFT JOIN a.incident i
           WHERE (:status IS NULL OR a.status = :status)
             AND (:incidentId IS NULL OR i.id = :incidentId)
           ORDER BY a.receivedAt DESC
           """)
    List<Alert> search(@Param("status") AlertStatus status,
                       @Param("incidentId") Long incidentId);

    List<Alert> findByIncidentIdOrderByReceivedAtAsc(Long incidentId);
}