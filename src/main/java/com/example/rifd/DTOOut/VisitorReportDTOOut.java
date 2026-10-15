package com.example.rifd.DTOOut;

import java.time.LocalDateTime;

// what a visitor may see about their own report: no incident code, staff names or internal notes.
// transcript: what we heard in the voice note, only in the answer to POST /visitor-reports/media (null when tracking)
// step: 0 RECEIVED, 1 ASSIGNED, 2 IN_PROGRESS, 3 RESOLVED / CLOSED
public record VisitorReportDTOOut(
        String trackingCode,
        int step,
        String state,
        String zoneCode,
        LocalDateTime receivedAt,
        LocalDateTime updatedAt,
        String transcript
) {
}