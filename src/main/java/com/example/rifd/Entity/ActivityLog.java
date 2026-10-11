package com.example.rifd.Entity;

import com.example.rifd.Enum.*;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "activity_logs")
@Getter
@Setter
@NoArgsConstructor
public class ActivityLog extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EntityType entityType;

    private Long entityId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id")
    private Incident incident;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActionType action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActorType actorType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_staff_id")
    private Staff actor;

    @Column(columnDefinition = "TEXT")
    private String oldValue;

    @Column(columnDefinition = "TEXT")
    private String newValue;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(name = "log_timestamp")
    private LocalDateTime timestamp;
}