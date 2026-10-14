package com.example.rifd.DTOIn;

import com.example.rifd.Enum.StaffStatus;
import jakarta.validation.constraints.NotNull;

public record StaffStatusDTOIn(
        @NotNull(message = "status is required")
        StaffStatus status
) {
}
