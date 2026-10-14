package com.example.rifd.DTOIn;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// manual assignment of a specific staff member
public record TaskDTOIn(
        @NotNull(message = "incidentId is required")
        Long incidentId,

        @NotNull(message = "staffId is required")
        Long staffId,

        @Size(max = 2000, message = "notes is too long")
        String notes,

        Long actorStaffId
) {
}
