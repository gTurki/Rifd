package com.example.rifd.Entity;

import com.example.rifd.Enum.*;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "incidents")
@Getter
@Setter
@NoArgsConstructor
public class Incident extends BaseEntity {

    @Column(unique = true)
    private String code;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Enumerated(EnumType.STRING)
    private IncidentCategory category;

    @Enumerated(EnumType.STRING)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentStatus status = IncidentStatus.OPEN;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private Zone zone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private Event event;

    private Double x;
    private Double y;

    private Integer alertCount = 0;

    private Boolean affectsAccessibility = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accessibility_asset_id")
    private AccessibilityAsset accessibilityAsset;

    private LocalDateTime acknowledgedAt;
    private LocalDateTime resolvedAt;

    @Column(columnDefinition = "TEXT")
    private String resolutionNotes;

    @Enumerated(EnumType.STRING)
    private Severity actualSeverity;
}