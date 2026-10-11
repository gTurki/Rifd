package com.example.rifd.DTO;

import com.example.rifd.Enum.ZoneType;

import java.time.LocalDateTime;

public record ZoneResponse(
        Long id,
        String code,
        String name,
        ZoneType type,
        String polygonCoordinates,
        Double centerX,
        Double centerY,
        Integer capacity,
        Integer currentOccupancy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

