package com.example.rifd.DTOIn;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// confirms a previewed web report. transcript = the sender's correction (blank = keep what we heard),
// zoneCode = the final choice (null = "I don't know")
public record ReportConfirmDTOIn(
        @NotBlank(message = "draftId is required")
        @Size(max = 64, message = "draftId is too long")
        String draftId,

        @Size(max = 1000, message = "transcript is too long")
        String transcript,

        @Size(max = 20, message = "zoneCode is too long")
        String zoneCode
) {
}
