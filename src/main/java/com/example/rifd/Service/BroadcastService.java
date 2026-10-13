package com.example.rifd.Service;

import com.example.rifd.Api.WsMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class BroadcastService {

    public static final String ALERTS = "/topic/alerts";
    public static final String INCIDENTS = "/topic/incidents";
    public static final String STATS = "/topic/stats";
    public static final String RECOMMENDATIONS = "/topic/recommendations";
    public static final String TASKS = "/topic/tasks";
    public static final String STAFF = "/topic/staff";
    public static final String ROUTES = "/topic/routes";
    public static final String NOTIFICATIONS = "/topic/notifications";

    private final SimpMessagingTemplate messagingTemplate;

    public void send(String topic, String type, Object payload) {
        WsMessage message = WsMessage.of(type, payload);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    messagingTemplate.convertAndSend(topic, message);
                }
            });
        } else {
            messagingTemplate.convertAndSend(topic, message);
        }
    }
}