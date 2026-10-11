package com.example.rifd.Api;

import java.time.LocalDateTime;

public record WsMessage(String type, Object payload, LocalDateTime timestamp) {

    public static WsMessage of(String type, Object payload) {
        return new WsMessage(type, payload, LocalDateTime.now());
    }
}