package com.example.rifd.DTOIn;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VisitorLoginDTOIn(
        @NotBlank(message = "email is required")
        @Size(max = 150, message = "email is too long")
        String email,

        @NotBlank(message = "password is required")
        @Size(max = 72, message = "password is too long")
        String password
) {
}
