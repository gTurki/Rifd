package com.example.rifd.DTOOut;

import com.example.rifd.Enum.RecommendationStatus;
import com.example.rifd.Enum.StaffLevel;
import com.example.rifd.Enum.StaffSpecialty;

import java.time.LocalDateTime;

public record RecommendationDTOOut(
        Long id,
        Long incidentId,
        Long playbookId,
        String playbookTitle,
        String actionText,
        String reasoning,
        StaffSpecialty requiredSpecialty,
        StaffLevel requiredLevel,
        Integer staffCount,
        Double confidence,
        RecommendationStatus status,
        String rejectionReason,
        Long decidedById,
        LocalDateTime decidedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
