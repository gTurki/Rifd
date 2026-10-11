package com.example.rifd.Entity;

import com.example.rifd.Enum.ZoneType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "zones")
@Getter
@Setter
@NoArgsConstructor
public class Zone extends BaseEntity {

    @Column(unique = true, nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    private ZoneType type;

    @Column(columnDefinition = "TEXT")
    private String polygonCoordinates;

    private Double centerX;
    private Double centerY;

    private Integer capacity;
    private Integer currentOccupancy;
}