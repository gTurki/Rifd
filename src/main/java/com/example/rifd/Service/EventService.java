package com.example.rifd.Service;

import com.example.rifd.Repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Developer 1 service scaffold. Behavior is added in the subsequent phases.
 */
@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
}

