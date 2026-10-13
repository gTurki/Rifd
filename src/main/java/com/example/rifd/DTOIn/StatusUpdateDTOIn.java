package com.example.rifd.DTOIn;

import com.example.rifd.Enum.IncidentStatus;
import jakarta.validation.constraints.NotNull;

public record StatusUpdateDTOIn(
        @NotNull(message = "status is required")
        IncidentStatus status,

        Long actorStaffId
) {
}