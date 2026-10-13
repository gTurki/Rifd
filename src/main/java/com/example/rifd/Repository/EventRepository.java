package com.example.rifd.Repository;

import com.example.rifd.Entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    // active = startTime <= now <= endTime
    @Query("""
           SELECT e FROM Event e
           WHERE e.startTime <= :now AND e.endTime >= :now
           ORDER BY e.startTime ASC
           """)
    List<Event> findActive(@Param("now") LocalDateTime now);

    List<Event> findAllByOrderByStartTimeAsc();
}