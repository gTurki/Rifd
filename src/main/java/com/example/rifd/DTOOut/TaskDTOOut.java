package com.example.rifd.DTOOut;

import com.example.rifd.Enum.Severity;
import com.example.rifd.Enum.StaffLevel;
import com.example.rifd.Enum.StaffSpecialty;
import com.example.rifd.Enum.TaskStatus;

import java.time.LocalDateTime;

public record TaskDTOOut(
        Long id,
        Long incidentId,
        String incidentCode,
        String incidentTitle,
        Severity incidentSeverity,
        Double incidentX,
        Double incidentY,
        String zoneName,
        Long staffId,
        String staffName,
        StaffSpecialty staffSpecialty,
        StaffLevel staffLevel,
        Long recommendationId,
        Long assignedById,
        TaskStatus status,
        Double distanceMeters,
        Integer estimatedArrivalMinutes,
        LocalDateTime assignedAt,
        LocalDateTime acceptedAt,
        LocalDateTime arrivedAt,
        LocalDateTime completedAt,
        String rejectionReason,
        Boolean isEscalated,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
