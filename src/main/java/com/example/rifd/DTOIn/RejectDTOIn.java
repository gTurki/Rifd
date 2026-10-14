package com.example.rifd.DTOIn;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// used to reject a recommendation or a task
public record RejectDTOIn(
        @NotBlank(message = "reason is required")
        @Size(max = 500, message = "reason is too long")
        String reason,

        Long actorStaffId
) {
}
