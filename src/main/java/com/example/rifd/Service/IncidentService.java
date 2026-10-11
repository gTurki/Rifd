package com.example.rifd.Service;

import com.example.rifd.Repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Developer 1 service scaffold. Behavior is added in the subsequent phases.
 */
@Service
@RequiredArgsConstructor
public class IncidentService {

    private final IncidentRepository incidentRepository;
}

