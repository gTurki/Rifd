package com.example.rifd.Controller;

import com.example.rifd.Service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Developer 1 controller scaffold. No endpoint methods are exposed yet.
 */
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;
}

