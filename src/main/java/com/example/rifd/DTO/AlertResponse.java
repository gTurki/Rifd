package com.example.rifd.DTO;

import com.example.rifd.Enum.AlertSource;
import com.example.rifd.Enum.AlertStatus;
import com.example.rifd.Enum.IncidentCategory;
import com.example.rifd.Enum.Severity;

import java.time.LocalDateTime;

public record AlertResponse(
        Long id,
        AlertSource source,
        String sourceReference,
        String rawText,
        String language,
        String attachmentUrl,
        Double x,
        Double y,
        Long zoneId,
        LocalDateTime receivedAt,
        LocalDateTime processedAt,
        Severity aiSeverity,
        IncidentCategory aiCategory,
        Double aiConfidence,
        AlertStatus status,
        Long incidentId,
        Double mergeScore,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

