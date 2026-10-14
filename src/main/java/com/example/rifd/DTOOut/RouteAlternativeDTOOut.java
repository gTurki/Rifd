package com.example.rifd.DTOOut;

import com.example.rifd.Enum.RouteStatus;

import java.util.List;

// found = false -> no accessible path right now (visitors are sent to the visitor services desk)
public record RouteAlternativeDTOOut(
        Long routeId,
        RouteStatus status,
        boolean found,
        List<RouteSegmentDTOOut> segments,
        Double totalMeters,
        Integer estimatedMinutes,
        String publicMessageAr,
        String publicMessageEn
) {
}
