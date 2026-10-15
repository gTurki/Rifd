package com.example.rifd.DTOIn;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VisitorReportDTOIn(
        @NotBlank(message = "text is required")
        @Size(max = 2000, message = "text is too long")
        String text,

        @Size(max = 10, message = "language is too long")
        String language,

        Double x,
        Double y,

        @Size(max = 20, message = "zoneCode is too long")
        String zoneCode
) {
}