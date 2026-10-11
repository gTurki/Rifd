package com.example.rifd.Repository;

import com.example.rifd.Entity.AccessibilityAsset;
import com.example.rifd.Enum.AssetStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccessibilityAssetRepository extends JpaRepository<AccessibilityAsset, Long> {

    List<AccessibilityAsset> findByZoneId(Long zoneId);

    List<AccessibilityAsset> findByStatus(AssetStatus status);
}