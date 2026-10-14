package com.example.rifd.DTOIn;

import jakarta.validation.constraints.NotBlank;

public record LoginDTOIn(
        @NotBlank(message = "username is required")
        String username,

        @NotBlank(message = "password is required")
        String password
) {
}
