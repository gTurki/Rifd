package com.example.rifd.Service;

import com.example.rifd.Repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class ZoneService {

    private final ZoneRepository zoneRepository;
}

