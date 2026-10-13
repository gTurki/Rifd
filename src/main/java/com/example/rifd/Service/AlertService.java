package com.example.rifd.Service;

import com.example.rifd.Repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;
}

