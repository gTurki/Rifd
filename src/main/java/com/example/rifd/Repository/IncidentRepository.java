package com.example.rifd.Repository;

import com.example.rifd.Entity.Incident;
import com.example.rifd.Enum.IncidentCategory;
import com.example.rifd.Enum.IncidentStatus;
import com.example.rifd.Enum.Severity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, Long> {

    @Query("""
           SELECT i FROM Incident i
           WHERE (:status IS NULL OR i.status = :status)
             AND (:severity IS NULL OR i.severity = :severity)
             AND (:category IS NULL OR i.category = :category)
           """)
    List<Incident> search(@Param("status") IncidentStatus status,
                          @Param("severity") Severity severity,
                          @Param("category") IncidentCategory category);

    // merge candidates: same category, still open
    List<Incident> findByCategoryAndStatusNotIn(IncidentCategory category, Collection<IncidentStatus> statuses);
}