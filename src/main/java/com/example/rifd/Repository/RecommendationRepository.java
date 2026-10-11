package com.example.rifd.Repository;

import com.example.rifd.Entity.Recommendation;
import com.example.rifd.Enum.RecommendationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

    List<Recommendation> findByIncidentIdOrderByCreatedAtDesc(Long incidentId);

    // when status changes, the recommendation should change
    List<Recommendation> findByIncidentIdAndStatus(Long incidentId, RecommendationStatus status);
}