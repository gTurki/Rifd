package com.example.rifd.DTOOut;

import com.example.rifd.Enum.NotificationType;
import com.example.rifd.Enum.Severity;
import com.example.rifd.Enum.StaffSpecialty;

import java.time.LocalDateTime;

public record NotificationDTOOut(
        Long id,
        Long incidentId,
        NotificationType type,
        Long recipientStaffId,
        StaffSpecialty recipientSpecialty,
        String message,
        Severity priority,
        LocalDateTime sentAt,
        LocalDateTime readAt
) {
}
