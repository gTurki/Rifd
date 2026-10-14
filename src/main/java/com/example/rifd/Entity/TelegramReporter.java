package com.example.rifd.Entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// one row per Telegram user who wrote to the bot. Report counts are not stored: computed from the alerts
@Entity
@Table(name = "telegram_reporters")
@Getter
@Setter
@NoArgsConstructor
public class TelegramReporter extends BaseEntity {

    @Column(nullable = false, unique = true)
    private Long telegramUserId;

    private String firstName;
    private String username;

    // verified Saudi mobile (+9665XXXXXXXX), shared by the user with Telegram's contact button
    private String phone;
    private LocalDateTime phoneVerifiedAt;

    @Column(nullable = false)
    private Boolean blocked = false;

    private LocalDateTime blockedAt;
    private String blockReason;

    // staff id who blocked; null = automatic
    private Long blockedBy;

    // false reports already forgiven at the last unblock: the auto-block counts only newer ones
    @Column(nullable = false)
    private Integer forgivenFalseReports = 0;

    private LocalDateTime lastMessageAt;
}