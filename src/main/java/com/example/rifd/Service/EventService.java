package com.example.rifd.Service;

import com.example.rifd.Repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
}

