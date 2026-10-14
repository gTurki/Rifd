package com.example.rifd.DTOIn;

import com.example.rifd.Enum.AssetStatus;
import jakarta.validation.constraints.NotNull;

public record AssetStatusDTOIn(
        @NotNull(message = "status is required")
        AssetStatus status,

        Long actorStaffId
) {
}
