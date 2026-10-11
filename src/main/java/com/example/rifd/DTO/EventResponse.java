package com.example.rifd.DTO;

import com.example.rifd.Enum.EventType;

import java.time.LocalDateTime;

public record EventResponse(
        Long id,
        String name,
        EventType type,
        Long zoneId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer expectedAttendance,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

