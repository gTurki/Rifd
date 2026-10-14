package com.example.rifd.Telegram;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

// in memory, per Telegram user: MAX_MESSAGES analyzed messages in a sliding WINDOW.
// Over the limit the user is told once, then ignored quietly (the bot must not spam back)
@Component
public class TelegramRateLimiter {

    public enum Result {
        ALLOWED,
        LIMITED_NOTIFY,
        LIMITED_SILENT
    }

    private static final int MAX_MESSAGES = 6;
    private static final Duration WINDOW = Duration.ofMinutes(10);

    private static final class UserWindow {
        private final Deque<Instant> hits = new ArrayDeque<>();
        private boolean notified;
    }

    private final Map<Long, UserWindow> windows = new HashMap<>();

    public synchronized Result hit(long telegramUserId) {
        Instant now = Instant.now();
        Instant from = now.minus(WINDOW);
        UserWindow window = windows.computeIfAbsent(telegramUserId, k -> new UserWindow());

        while (!window.hits.isEmpty() && window.hits.peekFirst().isBefore(from)) {
            window.hits.pollFirst();
        }

        if (window.hits.size() < MAX_MESSAGES) {
            window.hits.addLast(now);
            window.notified = false;
            return Result.ALLOWED;
        }
        if (window.notified) return Result.LIMITED_SILENT;
        window.notified = true;
        return Result.LIMITED_NOTIFY;
    }
}