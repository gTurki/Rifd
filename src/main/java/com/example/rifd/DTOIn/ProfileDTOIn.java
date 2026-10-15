package com.example.rifd.DTOIn;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record ProfileDTOIn(
        @Size(max = 20, message = "phone is too long")
        String phone,

        @Email(message = "email is invalid")
        @Size(max = 120, message = "email is too long")
        String email
) {
}