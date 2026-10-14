package com.example.rifd.DTOOut;

// one leg of an alternative path; fromZoneId/toZoneId follow the travel direction (a route can be walked both ways)
public record RouteSegmentDTOOut(
        Long routeId,
        String name,
        Long fromZoneId,
        Long toZoneId,
        String pathCoordinates,
        Double lengthMeters
) {
}
