package com.example.rifd.DTOOut;

public record SimulationStatusDTOOut(
        boolean running,
        int totalAlerts,
        int sentAlerts,
        double speed
) {
}
