package com.example.rifd.DTOOut;

import com.example.rifd.Enum.ReporterTrust;

import java.time.LocalDateTime;

public record TelegramReporterDTOOut(
        Long id,
        Long telegramUserId,
        String firstName,
        String username,
        String maskedPhone,
        Boolean phoneVerified,
        ReporterTrust trust,
        long reportCount,
        long confirmedCount,
        long falseReportCount,
        Boolean blocked,
        LocalDateTime blockedAt,
        String blockReason,
        Long blockedBy,
        LocalDateTime lastMessageAt,
        LocalDateTime createdAt
) {
}