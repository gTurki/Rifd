package com.example.rifd.DTOOut;

// the generated password is returned ONCE here and never again (only its BCrypt hash is stored)
public record StaffCreatedDTOOut(
        StaffDTOOut staff,
        String username,
        String password
) {
}