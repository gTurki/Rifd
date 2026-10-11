package com.example.rifd.Entity;

import com.example.rifd.Enum.*;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "accessibility_assets")
@Getter
@Setter
@NoArgsConstructor
public class AccessibilityAsset extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssetType type;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_id")
    private Zone zone;

    private Double x;
    private Double y;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssetStatus status = AssetStatus.OPERATIONAL;

    private LocalDateTime lastStatusChange;
    private LocalDateTime expectedRestoreTime;
}