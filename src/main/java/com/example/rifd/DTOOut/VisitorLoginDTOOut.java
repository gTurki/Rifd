package com.example.rifd.DTOOut;

public record VisitorLoginDTOOut(
        String token,
        Long visitorId,
        String name,
        String email,
        long expiresInMinutes
) {
}
