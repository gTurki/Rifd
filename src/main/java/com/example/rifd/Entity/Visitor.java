package com.example.rifd.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// a visitor account (visitor page sign-up). Not staff: its token carries role VISITOR and no staffId
@Entity
@Table(name = "visitors")
@Getter
@Setter
@NoArgsConstructor
public class Visitor extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    // stored lower case: one account per address
    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false)
    private String passwordHash;
}
