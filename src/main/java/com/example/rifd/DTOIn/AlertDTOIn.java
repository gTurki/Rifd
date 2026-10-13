package com.example.rifd.DTOIn;

import com.example.rifd.Enum.AlertSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record AlertDTOIn(
        @NotNull(message = "source is required")
        AlertSource source,

        @Size(max = 255, message = "sourceReference is too long")
        String sourceReference,

        @NotBlank(message = "text is required")
        @Size(max = 2000, message = "text is too long")
        String text,

        @Size(max = 10, message = "language is too long")
        String language,

        @Size(max = 255, message = "attachmentUrl is too long")
        String attachmentUrl,

        Double x,
        Double y,

        @Size(max = 20, message = "zoneCode is too long")
        String zoneCode,

        LocalDateTime receivedAt
) {
}