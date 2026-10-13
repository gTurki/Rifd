package com.example.rifd.DTOOut;

import java.util.List;

public record IncidentDetailsDTOOut(
        IncidentDTOOut incident,
        List<AlertDTOOut> alerts
) {
}