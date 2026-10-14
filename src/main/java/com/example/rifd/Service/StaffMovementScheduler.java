package com.example.rifd.Service;

import com.example.rifd.Entity.Incident;
import com.example.rifd.Entity.Staff;
import com.example.rifd.Entity.Task;
import com.example.rifd.Enum.TaskStatus;
import com.example.rifd.Repository.TaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

// demo: staff whose task is EN_ROUTE walk toward the incident; on arrival the task becomes ON_SITE
@Slf4j
@Component
public class StaffMovementScheduler {

    private static final long TICK_MS = 2000;
    private static final double ARRIVAL_DISTANCE = 5;

    private final TaskRepository taskRepository;
    private final StaffService staffService;
    private final TaskService taskService;
    private final SimulationService simulationService;
    private final TransactionTemplate tx;

    public StaffMovementScheduler(TaskRepository taskRepository, StaffService staffService, TaskService taskService,
                                  SimulationService simulationService, PlatformTransactionManager transactionManager) {
        this.taskRepository = taskRepository;
        this.staffService = staffService;
        this.taskService = taskService;
        this.simulationService = simulationService;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Value("${movement.enabled:true}")
    private boolean enabled;

    @Value("${assignment.walking-speed-mps:1.3}")
    private double walkingSpeed;

    // 1 = real walking speed; the demo default makes a 200 m walk take ~30 s instead of ~2.5 min
    @Value("${movement.speed-multiplier:5}")
    private double speedMultiplier;

    @Scheduled(fixedDelay = TICK_MS)
    public void tick() {
        if (!enabled || simulationService.isResetting()) return;
        List<Long> arrived = tx.execute(s -> step());
        if (arrived == null) return;
        for (Long taskId : arrived) {
            try {
                taskService.markArrived(taskId); // own transaction: ON_SITE + incident IN_PROGRESS
            } catch (Exception e) {
                log.warn("Arrival of task #{} failed: {}", taskId, e.getMessage());
            }
        }
    }

    // runs inside tx (lazy incident / staff)
    private List<Long> step() {
        double stepMeters = walkingSpeed * speedMultiplier * TICK_MS / 1000.0;
        List<Long> arrived = new ArrayList<>();

        for (Task task : taskRepository.findByStatus(TaskStatus.EN_ROUTE)) {
            Incident incident = task.getIncident();
            Staff staff = task.getStaff();
            double[] pos = StaffService.positionOf(staff);
            if (pos == null || incident.getX() == null || incident.getY() == null) continue;

            double dx = incident.getX() - pos[0];
            double dy = incident.getY() - pos[1];
            double distance = Math.hypot(dx, dy);

            if (distance <= ARRIVAL_DISTANCE + stepMeters) {
                staffService.moveTo(staff, incident.getX(), incident.getY());
                arrived.add(task.getId());
            } else {
                staffService.moveTo(staff, pos[0] + dx / distance * stepMeters, pos[1] + dy / distance * stepMeters);
            }
        }
        return arrived;
    }
}
