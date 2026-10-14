package com.example.rifd.Events;

// a closed incident (RESOLVED / FALSE_ALARM) is open again
public record IncidentReopenedEvent(Long incidentId) {
}