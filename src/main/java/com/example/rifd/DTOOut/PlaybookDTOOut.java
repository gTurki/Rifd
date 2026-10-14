package com.example.rifd.DTOOut;

import com.example.rifd.Enum.IncidentCategory;
import com.example.rifd.Enum.Severity;
import com.example.rifd.Enum.StaffLevel;
import com.example.rifd.Enum.StaffSpecialty;

import java.time.LocalDateTime;

public record PlaybookDTOOut(
        Long id,
        String title,
        IncidentCategory category,
        Severity minSeverity,
        String steps,
        StaffSpecialty requiredSpecialty,
        StaffLevel requiredLevel,
        Integer recommendedStaffCount,
        Integer targetResponseMinutes,
        Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
