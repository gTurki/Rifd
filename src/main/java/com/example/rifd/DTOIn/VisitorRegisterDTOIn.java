package com.example.rifd.DTOIn;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VisitorRegisterDTOIn(
        @NotBlank(message = "name is required")
        @Size(max = 100, message = "name is too long")
        String name,

        @NotBlank(message = "email is required")
        @Email(message = "email is not valid")
        @Size(max = 150, message = "email is too long")
        String email,

        @NotBlank(message = "password is required")
        @Size(min = 8, max = 72, message = "password must be 8 to 72 characters")
        String password
) {
}
