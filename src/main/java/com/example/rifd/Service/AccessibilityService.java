package com.example.rifd.Service;

import com.example.rifd.Entity.AccessibilityAsset;
import com.example.rifd.Repository.AccessibilityAssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AccessibilityService {

    private final AccessibilityAssetRepository assetRepository;

    // used when creating incidents
    @Transactional(readOnly = true)
    public Optional<AccessibilityAsset> findNearestAsset(Double x, Double y, Long zoneId, double radius) {
        if (x == null || y == null) return Optional.empty();

        if (zoneId != null) {
            Optional<AccessibilityAsset> inZone = nearest(assetRepository.findByZoneId(zoneId), x, y, radius);
            if (inZone.isPresent()) return inZone;
        }
        return nearest(assetRepository.findAll(), x, y, radius);
    }

    private Optional<AccessibilityAsset> nearest(List<AccessibilityAsset> assets, double x, double y, double radius) {
        return assets.stream()
                .filter(a -> a.getX() != null && a.getY() != null)
                .filter(a -> distance(a.getX(), a.getY(), x, y) <= radius)
                .min(Comparator.comparingDouble(a -> distance(a.getX(), a.getY(), x, y)));
    }

    public static double distance(double x1, double y1, double x2, double y2) {
        return Math.hypot(x1 - x2, y1 - y2);
    }
}