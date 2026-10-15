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
import com.example.rifd.Service.VisionClient.ImageData;
import com.example.rifd.Service.VisionClient.VisionResult;
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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.util.StringUtils.hasText;

@Slf4j
@Service
public class SimulationService {

    // one line of a scenario file (same fields as AlertDTOIn + offsetSeconds).
    // image = a camera frame under resources/scenario/images: it is analyzed by AI 2 first,
    // and only a fire/smoke detection becomes an alert (a smart camera stays silent otherwise)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScenarioAlert(int offsetSeconds, AlertSource source, String sourceReference, String text,
                                String language, String attachmentUrl, Double x, Double y, String zoneCode,
                                String image) {
        AlertDTOIn toDTO(String text, String attachmentUrl) {
            return new AlertDTOIn(source, sourceReference, text, language, attachmentUrl, x, y, zoneCode, null);
        }
    }

    // attachmentUrl of a camera alert = scenario:<file name>, served by /alerts/{id}/attachment
    public static final String IMAGE_PREFIX = "scenario:";
    public static final String IMAGE_DIR = "scenario/images/";
    // ExpoGuard accepts only VISITOR_APP as source (CCTV -> INVALID_REQUEST); the alert itself stays a camera alert
    private static final String VISION_SOURCE = "VISITOR_APP";

    // ?scenario=demo (the 40 -> 3 story) or ?scenario=fire (smart cameras at Gate 1)
    private static final Map<String, String> SCENARIOS = Map.of(
            "demo", "scenario/demo.json",
            "fire", "scenario/fire.json");

    private final AlertService alertService;
    private final AccessibilityService accessibilityService;
    private final DashboardService dashboardService;
    private final BroadcastService broadcastService;
    private final VisionClient visionClient;
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

    private volatile boolean resetting;

    public boolean isResetting() {
        return resetting;
    }

    public SimulationService(AlertService alertService, AccessibilityService accessibilityService,
                             DashboardService dashboardService, BroadcastService broadcastService,
                             VisionClient visionClient,
                             NotificationRepository notificationRepository, TaskRepository taskRepository,
                             RecommendationRepository recommendationRepository, ActivityLogRepository activityLogRepository,
                             AlertRepository alertRepository, IncidentRepository incidentRepository,
                             StaffRepository staffRepository, EventRepository eventRepository,
                             PlatformTransactionManager transactionManager) {
        this.alertService = alertService;
        this.accessibilityService = accessibilityService;
        this.dashboardService = dashboardService;
        this.broadcastService = broadcastService;
        this.visionClient = visionClient;
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

    public synchronized SimulationStatusDTOOut start(double speed, String scenario) {
        if (isRunning()) throw new ApiException("Simulation is already running");
        String file = SCENARIOS.get(scenario == null ? "demo" : scenario.trim().toLowerCase(Locale.ROOT));
        if (file == null) throw new ApiException("Unknown scenario, use one of: " + SCENARIOS.keySet());
        List<ScenarioAlert> alerts = load(file);

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
        log.info("Simulation '{}' started: {} items", file, alerts.size());
        broadcastService.send(BroadcastService.STATS, "SIMULATION_STARTED", status());
        return status();
    }

    // clears all operational data (alerts, incidents, log, tasks, recommendations, notifications) and puts
    // staff, assets and routes back to their initial state. Zones, events, staff, playbooks stay.
    public synchronized SimulationStatusDTOOut reset() {
        resetting = true; // the schedulers skip their ticks while the tables are cleared
        try {
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

                // bulk update: no @Version conflict with the movement / escalation schedulers
                LocalDateTime now = LocalDateTime.now();
                for (Staff staff : staffRepository.findAll()) {
                    double[] home = DataSeeder.homePosition(staff.getHomeZone(), staff.getEmployeeNumber());
                    staffRepository.resetStaff(staff.getId(), StaffStatus.AVAILABLE,
                            home == null ? staff.getCurrentX() : home[0],
                            home == null ? staff.getCurrentY() : home[1], now);
                }
                ensureActiveEvent();
                return null;
            }));
        } finally {
            resetting = false;
        }

        broadcastService.send(BroadcastService.STATS, "SIMULATION_RESET", status());
        dashboardService.broadcastStats();
        return status();
    }

    public SimulationStatusDTOOut status() {
        return new SimulationStatusDTOOut(isRunning(), total, sent.get(), speed);
    }

    // ---------------- images (also used by the attachment endpoint) ----------------

    // file name only: no folders, no "..", so nothing outside scenario/images can be read
    public static boolean isSafeImageName(String name) {
        return name != null && name.matches("[A-Za-z0-9_.-]{1,100}") && !name.contains("..");
    }

    public static byte[] readImage(String name) {
        if (!isSafeImageName(name)) return null;
        try (InputStream in = new ClassPathResource(IMAGE_DIR + name).getInputStream()) {
            return in.readAllBytes();
        } catch (Exception e) {
            return null;
        }
    }

    public static String imageType(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    // ---------------- helpers ----------------

    private void send(ScenarioAlert a) {
        try {
            if (hasText(a.image())) sendCamera(a);
            else alertService.ingest(a.toDTO(a.text(), a.attachmentUrl()));
        } catch (Exception e) {
            log.error("Simulation alert '{}' failed", a.sourceReference(), e);
        } finally {
            sent.incrementAndGet();
        }
    }

    // smart camera: AI 2 looks at the frame first. Detection -> alert (with the frame attached).
    // Nothing detected, or AI 2 unavailable -> no alert (the human reports in the scenario still open the incident)
    private void sendCamera(ScenarioAlert a) {
        byte[] bytes = readImage(a.image());
        if (bytes == null) {
            log.warn("Simulation camera {}: image '{}' not found in resources/{}", a.sourceReference(), a.image(), IMAGE_DIR);
            return;
        }

        Optional<VisionResult> result = visionClient.analyzeAll(
                List.of(new ImageData(bytes, imageType(a.image()))),
                "sim-" + a.sourceReference(), VISION_SOURCE, Instant.now());
        String detection = result.filter(VisionResult::hasFindings).map(VisionResult::summaryAr).orElse(null);

        if (detection == null) {
            log.info("Simulation camera {}: {} -> no alert", a.sourceReference(),
                    result.isEmpty() ? "vision unavailable" : "nothing detected");
            return;
        }
        alertService.ingest(a.toDTO(a.text() + "\n" + detection, IMAGE_PREFIX + a.image()));
    }

    private boolean isRunning() {
        return futures.stream().anyMatch(f -> !f.isDone());
    }

    private List<ScenarioAlert> load(String file) {
        try (InputStream in = new ClassPathResource(file).getInputStream()) {
            List<ScenarioAlert> alerts = jsonMapper.readValue(in, new TypeReference<List<ScenarioAlert>>() {
            });
            if (alerts == null || alerts.isEmpty()) throw new ApiException("Scenario file is empty");
            return alerts.stream().sorted(Comparator.comparingInt(ScenarioAlert::offsetSeconds)).toList();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Cannot read {}", file, e);
            throw new ApiException("Cannot read the scenario " + file);
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