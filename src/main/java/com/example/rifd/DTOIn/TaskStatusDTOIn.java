package com.example.rifd.DTOIn;

import com.example.rifd.Enum.TaskStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// EN_ROUTE / ON_SITE / DONE
public record TaskStatusDTOIn(
        @NotNull(message = "status is required")
        TaskStatus status,

        @Size(max = 2000, message = "notes is too long")
        String notes,

        Long actorStaffId
) {
}
