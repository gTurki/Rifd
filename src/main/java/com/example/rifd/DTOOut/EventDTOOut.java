package com.example.rifd.DTOOut;

import com.example.rifd.Enum.EventType;

import java.time.LocalDateTime;

public record EventDTOOut(
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

