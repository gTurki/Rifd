package com.example.rifd.Entity;

import com.example.rifd.Enum.RouteStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "accessible_routes")
@Getter
@Setter
@NoArgsConstructor
public class AccessibleRoute extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_zone_id")
    private Zone fromZone;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_zone_id")
    private Zone toZone;

    @Column(columnDefinition = "TEXT")
    private String pathCoordinates;

    private Double lengthMeters;
    private Integer estimatedMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RouteStatus status = RouteStatus.ACTIVE;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "route_assets",
            joinColumns = @JoinColumn(name = "route_id"),
            inverseJoinColumns = @JoinColumn(name = "asset_id")
    )
    private List<AccessibilityAsset> assets = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alternative_route_id")
    private AccessibleRoute alternativeRoute;

    @Column(columnDefinition = "TEXT")
    private String publicMessageAr;

    @Column(columnDefinition = "TEXT")
    private String publicMessageEn;

    private LocalDateTime lastUpdated;

    // operator confirmed the alternative for the visitor app; cleared when the route changes again
    private LocalDateTime publishedAt;
}