package com.example.rifd.DTOOut;

import com.example.rifd.Enum.StaffLevel;
import com.example.rifd.Enum.StaffRole;
import com.example.rifd.Enum.StaffSpecialty;
import com.example.rifd.Enum.StaffStatus;

import java.time.LocalDateTime;
import java.time.LocalTime;

// no passwordHash / version
public record StaffDTOOut(
        Long id,
        String employeeNumber,
        String name,
        String phone,
        StaffRole role,
        StaffSpecialty specialty,
        StaffLevel level,
        StaffStatus status,
        Long homeZoneId,
        Double currentX,
        Double currentY,
        LocalDateTime lastLocationUpdate,
        LocalTime shiftStart,
        LocalTime shiftEnd,
        String username,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
