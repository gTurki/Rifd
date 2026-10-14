package com.example.rifd.DTOOut;

import com.example.rifd.Enum.StaffRole;

public record LoginDTOOut(
        String token,
        Long staffId,
        String name,
        StaffRole role,
        long expiresInMinutes
) {
}
