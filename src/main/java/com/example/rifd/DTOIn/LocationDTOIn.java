package com.example.rifd.DTOIn;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record LocationDTOIn(
        @NotNull(message = "x is required")
        @PositiveOrZero(message = "x must be zero or positive")
        Double x,

        @NotNull(message = "y is required")
        @PositiveOrZero(message = "y must be zero or positive")
        Double y
) {
}
