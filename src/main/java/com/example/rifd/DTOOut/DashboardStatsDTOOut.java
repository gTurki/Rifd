package com.example.rifd.DTOOut;

import com.example.rifd.Enum.Severity;

import java.util.Map;

public record DashboardStatsDTOOut(
        long totalAlerts,
        long mergedAlerts,
        long totalIncidents,
        long openIncidents,
        Map<Severity, Long> bySeverity,   // open incidents only, every level present (0 when none)
        long aiDecisions,          // classification by the AI
        long fallbackDecisions,    // classification by the rules
        long aiMerges,             // merges decided by the AI
        long fallbackMerges        // merges decided by the rules
) {
}
