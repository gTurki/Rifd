package com.example.rifd.Events;

import com.example.rifd.Enum.IncidentStatus;

public record IncidentResolvedEvent(Long incidentId, IncidentStatus finalStatus) {
}