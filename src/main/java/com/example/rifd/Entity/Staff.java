package com.example.rifd.Entity;

import com.example.rifd.Enum.*;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "staff")
@Getter @Setter @NoArgsConstructor
public class Staff extends BaseEntity {

    @Column(unique = true, nullable = false)
    private String employeeNumber;

    @Column(nullable = false)
    private String name;

    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StaffRole role;

    @Enumerated(EnumType.STRING)
    private StaffSpecialty specialty;

    @Enumerated(EnumType.STRING)
    private StaffLevel level;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StaffStatus status = StaffStatus.AVAILABLE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "home_zone_id")
    private Zone homeZone;

    private Double currentX;
    private Double currentY;
    private LocalDateTime lastLocationUpdate;

    private LocalTime shiftStart;
    private LocalTime shiftEnd;

    @Column(unique = true)
    private String username;

    private String passwordHash;

    @Version
    private Long version;
}