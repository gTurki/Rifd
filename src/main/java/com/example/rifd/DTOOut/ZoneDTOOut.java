package com.example.rifd.DTOOut;

import com.example.rifd.Enum.ZoneType;

import java.time.LocalDateTime;

public record ZoneDTOOut(
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

