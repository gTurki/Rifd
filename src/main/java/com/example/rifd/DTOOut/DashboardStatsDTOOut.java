package com.example.rifd.DTOOut;

import com.example.rifd.Enum.Severity;

import java.util.Map;

public record DashboardStatsDTOOut(
        long totalAlerts,
        long mergedAlerts,
        long totalIncidents,
        long openIncidents,
        Map<Severity, Long> bySeverity,   // open incidents only, every level present (0 when none)
        long aiDecisions,
        long fallbackDecisions
) {
}
