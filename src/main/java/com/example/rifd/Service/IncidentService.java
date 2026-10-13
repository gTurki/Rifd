package com.example.rifd.Service;

import com.example.rifd.Repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class IncidentService {

    private final IncidentRepository incidentRepository;
}

