package com.example.rifd.DTOIn;

import com.example.rifd.Enum.Severity;
import jakarta.validation.constraints.Size;

public record ResolveDTOIn(
        @Size(max = 2000, message = "resolutionNotes is too long")
        String resolutionNotes,

        Severity actualSeverity,

        Long actorStaffId
) {
}