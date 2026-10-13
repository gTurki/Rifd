package com.example.rifd.DTOIn;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AlertBatchDTOIn(
        @NotEmpty(message = "alerts must not be empty")
        @Size(max = 200, message = "a batch can contain at most 200 alerts")
        List<@Valid AlertDTOIn> alerts
) {
}