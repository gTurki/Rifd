package com.example.rifd.Service;

import com.example.rifd.Entity.Task;
import com.example.rifd.Enum.TaskStatus;
import com.example.rifd.Repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

// PENDING tasks not accepted within escalation.accept-timeout-seconds -> re-assigned + supervisors notified
@Slf4j
@Component
@RequiredArgsConstructor
public class EscalationScheduler {

    private final TaskRepository taskRepository;
    private final TaskService taskService;
    private final SimulationService simulationService;

    @Value("${escalation.enabled:true}")
    private boolean enabled;

    @Value("${escalation.accept-timeout-seconds:120}")
    private long timeoutSeconds;

    @Scheduled(fixedDelayString = "${escalation.check-interval-ms:30000}", initialDelay = 30000)
    public void escalateOverdueTasks() {
        if (!enabled || simulationService.isResetting()) return;
        LocalDateTime deadline = LocalDateTime.now().minusSeconds(timeoutSeconds);
        for (Task task : taskRepository.findByStatusAndIsEscalatedFalseAndAssignedAtBefore(TaskStatus.PENDING, deadline)) {
            try {
                taskService.escalateIfOverdue(task.getId(), deadline); // own transaction per task
            } catch (Exception e) {
                log.warn("Escalation of task #{} failed: {}", task.getId(), e.getMessage());
            }
        }
    }
}
