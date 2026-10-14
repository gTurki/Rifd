package com.example.rifd.Entity;

import com.example.rifd.Enum.*;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "alerts")
@Getter
@Setter
@NoArgsConstructor
public class Alert extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertSource source;

    private String sourceReference;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String rawText;

    private String language;

    // tg:<file_id> or tg:<id1>,<id2>,<id3> for a Telegram album (3 ids exceed 255)
    @Column(length = 1000)
    private String attachmentUrl;

    private Double x;
    private Double y;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private Zone zone;

    private LocalDateTime receivedAt;
    private LocalDateTime processedAt;

    @Enumerated(EnumType.STRING)
    private Severity aiSeverity;

    @Enumerated(EnumType.STRING)
    private IncidentCategory aiCategory;

    private Double aiConfidence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertStatus status = AlertStatus.NEW;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id")
    private Incident incident;

    private Double mergeScore;

    // Telegram only: who sent it, and how trusted they were at that moment
    @ManyToOne
    @JoinColumn(name = "reporter_id")
    private TelegramReporter reporter;

    @Enumerated(EnumType.STRING)
    private ReporterTrust reporterTrust;
}