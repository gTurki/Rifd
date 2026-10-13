package com.example.rifd.Service;

import com.example.rifd.DTOOut.EventDTOOut;
import com.example.rifd.Entity.Event;
import com.example.rifd.Mapper.DTOMapper;
import com.example.rifd.Repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;

    @Transactional(readOnly = true)
    public List<EventDTOOut> getAll(Boolean active) {
        List<Event> events = Boolean.TRUE.equals(active)
                ? getActiveEvents()
                : eventRepository.findAllByOrderByStartTimeAsc();
        return events.stream().map(DTOMapper::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public List<Event> getActiveEvents() {
        return eventRepository.findActive(LocalDateTime.now());
    }
}