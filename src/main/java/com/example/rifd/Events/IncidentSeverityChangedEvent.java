package com.example.rifd.Events;

import com.example.rifd.Enum.Severity;

public record IncidentSeverityChangedEvent(Long incidentId, Severity oldSeverity, Severity newSeverity) {
}