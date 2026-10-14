package com.example.rifd.DTOOut;

import com.example.rifd.Enum.RouteStatus;

import java.time.LocalDateTime;
import java.util.List;

public record AccessibleRouteDTOOut(
        Long id,
        String name,
        Long fromZoneId,
        Long toZoneId,
        String pathCoordinates,
        Double lengthMeters,
        Integer estimatedMinutes,
        RouteStatus status,
        List<Long> assetIds,
        Long alternativeRouteId,
        String publicMessageAr,
        String publicMessageEn,
        LocalDateTime lastUpdated,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
