package com.example.rifd.Entity;

import com.example.rifd.Enum.*;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "recommendations")
@Getter
@Setter
@NoArgsConstructor
public class Recommendation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id")
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "playbook_id")
    private Playbook playbook;

    @Column(columnDefinition = "TEXT")
    private String actionText;

    @Column(columnDefinition = "TEXT")
    private String reasoning;

    @Enumerated(EnumType.STRING)
    private StaffSpecialty requiredSpecialty;

    @Enumerated(EnumType.STRING)
    private StaffLevel requiredLevel;

    private Integer staffCount;

    private Double confidence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecommendationStatus status = RecommendationStatus.PENDING;

    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by_id")
    private Staff decidedBy;

    private LocalDateTime decidedAt;
}