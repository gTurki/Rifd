package com.example.rifd.DTO;

import com.example.rifd.Enum.ActionType;
import com.example.rifd.Enum.ActorType;
import com.example.rifd.Enum.EntityType;

import java.time.LocalDateTime;

public record ActivityLogResponse(
        Long id,
        EntityType entityType,
        Long entityId,
        Long incidentId,
        ActionType action,
        ActorType actorType,
        Long actorStaffId,
        String oldValue,
        String newValue,
        String details,
        LocalDateTime timestamp,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

