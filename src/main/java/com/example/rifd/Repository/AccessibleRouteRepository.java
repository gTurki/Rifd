package com.example.rifd.Repository;

import com.example.rifd.Entity.AccessibleRoute;
import com.example.rifd.Enum.RouteStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccessibleRouteRepository extends JpaRepository<AccessibleRoute, Long> {

    // to find all accessible routes for a specific asset
    List<AccessibleRoute> findByAssets_Id(Long assetId);

    List<AccessibleRoute> findByStatus(RouteStatus status);
}