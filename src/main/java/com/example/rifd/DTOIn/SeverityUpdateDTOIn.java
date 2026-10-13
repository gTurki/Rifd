package com.example.rifd.DTOIn;

import com.example.rifd.Enum.Severity;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SeverityUpdateDTOIn(
        @NotNull(message = "severity is required")
        Severity severity,

        @Size(max = 500, message = "reason is too long")
        String reason,

        Long actorStaffId
) {
}