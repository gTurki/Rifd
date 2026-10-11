package com.example.rifd.Entity;

import com.example.rifd.Enum.*;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "playbooks")
@Getter
@Setter
@NoArgsConstructor
public class Playbook extends BaseEntity {

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity minSeverity;

    @Column(columnDefinition = "TEXT")
    private String steps;

    @Enumerated(EnumType.STRING)
    private StaffSpecialty requiredSpecialty;

    @Enumerated(EnumType.STRING)
    private StaffLevel requiredLevel;

    private Integer recommendedStaffCount = 1;

    private Integer targetResponseMinutes;

    private Boolean isActive = true;
}