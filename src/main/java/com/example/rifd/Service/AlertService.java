package com.example.rifd.Service;

import com.example.rifd.Repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Developer 1 service scaffold. Behavior is added in the subsequent phases.
 */
@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;
}

