package com.example.rifd.DTOOut;

import com.example.rifd.Enum.IncidentCategory;
import com.example.rifd.Enum.IncidentStatus;
import com.example.rifd.Enum.Severity;

import java.time.LocalDateTime;

public record IncidentDTOOut(
        Long id,
        String code,
        String title,
        String summary,
        IncidentCategory category,
        Severity severity,
        IncidentStatus status,
        Long zoneId,
        Long eventId,
        Double x,
        Double y,
        Integer alertCount,
        Boolean affectsAccessibility,
        Long accessibilityAssetId,
        LocalDateTime acknowledgedAt,
        LocalDateTime resolvedAt,
        String resolutionNotes,
        Severity actualSeverity,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long possibleDuplicateOfId,
        String possibleDuplicateOfCode,
        Double duplicateScore
) {
}

