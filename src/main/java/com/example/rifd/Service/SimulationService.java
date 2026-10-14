package com.example.rifd.Service;

import com.example.rifd.Api.ApiException;
import com.example.rifd.Config.DataSeeder;
import com.example.rifd.DTOIn.AlertDTOIn;
import com.example.rifd.DTOOut.SimulationStatusDTOOut;
import com.example.rifd.Entity.Event;
import com.example.rifd.Entity.Staff;
import com.example.rifd.Enum.AlertSource;
import com.example.rifd.Enum.EventType;
import com.example.rifd.Enum.StaffStatus;
import com.example.rifd.Repository.ActivityLogRepository;
import com.example.rifd.Repository.AlertRepository;
import com.example.rifd.Repository.EventRepository;
import com.example.rifd.Repository.IncidentRepository;
import com.example.rifd.Repository.NotificationRepository;
import com.example.rifd.Repository.RecommendationRepository;
import com.example.rifd.Repository.StaffRepository;
import com.example.rifd.Repository.TaskRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class SimulationService {

    // one line of resources/scenario/demo.json (same fields as AlertDTOIn + offsetSeconds)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScenarioAlert(int offsetSeconds, AlertSource source, String sourceReference, String text,
                                String language, String attachmentUrl, Double x, Double y, String zoneCode) {
        AlertDTOIn toDTO() {
            return new AlertDTOIn(source, sourceReference, text, language, attachmentUrl, x, y, zoneCode, null);
        }
    }

    private static final String SCENARIO = "scenario/demo.json";

    private final AlertService alertService;
    private final AccessibilityService accessibilityService;
    private final DashboardService dashboardService;
    private final BroadcastService broadcastService;
    private final NotificationRepository notificationRepository;
    private final TaskRepository taskRepository;
    private final RecommendationRepository recommendationRepository;
    private final ActivityLogRepository activityLogRepository;
    private final AlertRepository alertRepository;
    private final IncidentRepository incidentRepository;
    private final StaffRepository staffRepository;
    private final EventRepository eventRepository;
    private final TransactionTemplate tx;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    // ONE thread: alerts are ingested strictly in scenario order
    private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    private final List<ScheduledFuture<?>> futures = new CopyOnWriteArrayList<>();
    private final AtomicInteger sent = new AtomicInteger();
    private volatile int total;
    private volatile double speed = 1;

    public SimulationService(AlertService alertService, AccessibilityService accessibilityService,
                             DashboardService dashboardService, BroadcastService broadcastService,
                             NotificationRepository notificationRepository, TaskRepository taskRepository,
                             RecommendationRepository recommendationRepository, ActivityLogRepository activityLogRepository,
                             AlertRepository alertRepository, IncidentRepository incidentRepository,
                             StaffRepository staffRepository, EventRepository eventRepository,
                             PlatformTransactionManager transactionManager) {
        this.alertService = alertService;
        this.accessibilityService = accessibilityService;
        this.dashboardService = dashboardService;
        this.broadcastService = broadcastService;
        this.notificationRepository = notificationRepository;
        this.taskRepository = taskRepository;
        this.recommendationRepository = recommendationRepository;
        this.activityLogRepository = activityLogRepository;
        this.alertRepository = alertRepository;
        this.incidentRepository = incidentRepository;
        this.staffRepository = staffRepository;
        this.eventRepository = eventRepository;
        this.tx = new TransactionTemplate(transactionManager);

        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("simulation-");
        scheduler.initialize();
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
    }

    public synchronized SimulationStatusDTOOut start(double speed) {
        if (isRunning()) throw new ApiException("Simulation is already running");
        List<ScenarioAlert> alerts = load();

        futures.clear();
        sent.set(0);
        total = alerts.size();
        this.speed = speed;
        ensureActiveEvent(); // the ceremony must be live, otherwise the crowd incident is not raised to HIGH

        Instant start = Instant.now();
        for (ScenarioAlert a : alerts) {
            long delayMs = Math.round(Math.max(0, a.offsetSeconds()) * 1000 / speed);
            futures.add(scheduler.schedule(() -> send(a), start.plusMillis(delayMs)));
        }
        broadcastService.send(BroadcastService.STATS, "SIMULATION_STARTED", status());
        return status();
    }

    // clears all operational data (alerts, incidents, log, tasks, recommendations, notifications) and puts
    // staff, assets and routes back to their initial state. Zones, events, staff, playbooks stay.
    public synchronized SimulationStatusDTOOut reset() {
        futures.forEach(f -> f.cancel(false));
        futures.clear();
        sent.set(0);
        total = 0;

        alertService.runExclusive(() -> tx.execute(s -> {
            // FK order: Notification -> Task -> Recommendation -> ActivityLog -> Alert -> Incident
            notificationRepository.deleteAllInBatch();
            taskRepository.deleteAllInBatch();
            recommendationRepository.deleteAllInBatch();
            activityLogRepository.deleteAllInBatch();
            alertRepository.deleteAllInBatch();
            incidentRepository.deleteAllInBatch();

            accessibilityService.resetAll();

            for (Staff staff : staffRepository.findAll()) {
                staff.setStatus(StaffStatus.AVAILABLE);
                double[] home = DataSeeder.homePosition(staff.getHomeZone(), staff.getEmployeeNumber());
                if (home != null) {
                    staff.setCurrentX(home[0]);
                    staff.setCurrentY(home[1]);
                }
                staff.setLastLocationUpdate(LocalDateTime.now());
            }
            ensureActiveEvent();
            return null;
        }));

        broadcastService.send(BroadcastService.STATS, "SIMULATION_RESET", status());
        dashboardService.broadcastStats();
        return status();
    }

    public SimulationStatusDTOOut status() {
        return new SimulationStatusDTOOut(isRunning(), total, sent.get(), speed);
    }

    // ---------------- helpers ----------------

    private void send(ScenarioAlert a) {
        try {
            alertService.ingest(a.toDTO());
        } catch (Exception e) {
            log.error("Simulation alert '{}' failed", a.sourceReference(), e);
        } finally {
            sent.incrementAndGet();
        }
    }

    private boolean isRunning() {
        return futures.stream().anyMatch(f -> !f.isDone());
    }

    private List<ScenarioAlert> load() {
        try (InputStream in = new ClassPathResource(SCENARIO).getInputStream()) {
            List<ScenarioAlert> alerts = jsonMapper.readValue(in, new TypeReference<List<ScenarioAlert>>() {
            });
            if (alerts == null || alerts.isEmpty()) throw new ApiException("Scenario file is empty");
            return alerts.stream().sorted(Comparator.comparingInt(ScenarioAlert::offsetSeconds)).toList();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Cannot read {}", SCENARIO, e);
            throw new ApiException("Cannot read the demo scenario");
        }
    }

    // rehearsals happen hours after startup: move the ceremony back to "now" if no event is live
    private void ensureActiveEvent() {
        tx.execute(s -> {
            if (!eventRepository.findActive(LocalDateTime.now()).isEmpty()) return null;
            eventRepository.findAllByOrderByStartTimeAsc().stream()
                    .filter(e -> e.getType() == EventType.CEREMONY)
                    .findFirst()
                    .ifPresent(this::reanchor);
            return null;
        });
    }

    private void reanchor(Event event) {
        event.setStartTime(LocalDateTime.now().minusMinutes(30));
        event.setEndTime(LocalDateTime.now().plusHours(12));
        eventRepository.save(event);
        log.info("Event '{}' re-anchored to now for the demo", event.getName());
    }
}
