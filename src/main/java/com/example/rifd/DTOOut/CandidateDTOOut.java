package com.example.rifd.DTOOut;

// preview of the nearest suitable staff (no assignment). escort = assistant that would join a HIGH incident
public record CandidateDTOOut(
        StaffDTOOut staff,
        Double distanceMeters,
        Integer estimatedArrivalMinutes,
        boolean escort
) {
}
