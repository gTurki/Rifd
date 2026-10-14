package com.example.rifd.Repository;

import com.example.rifd.Entity.AccessibleRoute;
import com.example.rifd.Enum.RouteStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccessibleRouteRepository extends JpaRepository<AccessibleRoute, Long> {

    // to find all accessible routes for a specific asset
    List<AccessibleRoute> findByAssets_Id(Long assetId);

    List<AccessibleRoute> findByStatus(RouteStatus status);

    // routes + their assets in one query (Dijkstra and the DTOs need the assets)
    @EntityGraph(attributePaths = "assets")
    @Query("SELECT DISTINCT r FROM AccessibleRoute r ORDER BY r.id")
    List<AccessibleRoute> findAllWithAssets();
}
