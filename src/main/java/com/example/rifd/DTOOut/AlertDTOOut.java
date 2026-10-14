package com.example.rifd.DTOOut;

import com.example.rifd.Enum.*;

import java.time.LocalDateTime;

public record AlertDTOOut(
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
        LocalDateTime updatedAt,
        Long reporterId,
        ReporterTrust reporterTrust
) {
}

