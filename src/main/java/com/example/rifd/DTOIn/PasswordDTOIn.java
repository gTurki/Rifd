package com.example.rifd.DTOIn;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordDTOIn(
        @NotBlank(message = "currentPassword is required")
        String currentPassword,

        @NotBlank(message = "newPassword is required")
        @Size(min = 8, max = 100, message = "newPassword must be 8 to 100 characters")
        String newPassword
) {
}