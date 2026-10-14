package com.example.rifd.DTOOut;

import com.example.rifd.Enum.AssetStatus;
import com.example.rifd.Enum.AssetType;

import java.time.LocalDateTime;

public record AccessibilityAssetDTOOut(
        Long id,
        String name,
        AssetType type,
        Long zoneId,
        Double x,
        Double y,
        AssetStatus status,
        LocalDateTime lastStatusChange,
        LocalDateTime expectedRestoreTime,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
