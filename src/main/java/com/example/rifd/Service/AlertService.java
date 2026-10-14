package com.example.rifd.Service;

import com.example.rifd.Api.ApiException;
import com.example.rifd.DTOIn.AlertDTOIn;
import com.example.rifd.DTOOut.AlertDTOOut;
import com.example.rifd.Entity.Alert;
import com.example.rifd.Entity.Incident;
import com.example.rifd.Entity.TelegramReporter;
import com.example.rifd.Entity.Zone;
import com.example.rifd.Enum.*;
import com.example.rifd.Mapper.DTOMapper;
import com.example.rifd.Repository.AlertRepository;
import com.example.rifd.Repository.ZoneRepository;
import com.example.rifd.Service.RuleBasedClassifier.Classification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

import static org.springframework.util.StringUtils.hasText;

@Service
public class AlertService {

    private final AlertRepository alertRepository;
    private final ZoneRepository zoneRepository;
    private final IncidentService incidentService;
    private final MergeService mergeService;
    private final AiClient aiClient;
    private final RuleBasedClassifier ruleBasedClassifier;
    private final ActivityLogService activityLogService;
    private final BroadcastService broadcastService;
    private final DashboardService dashboardService;
    private final TransactionTemplate transactionTemplate;

    // merge/create runs one alert at a time so the same event never opens two incidents
    private final ReentrantLock pipelineLock = new ReentrantLock(true);

    public AlertService(AlertRepository alertRepository, ZoneRepository zoneRepository,
                        IncidentService incidentService, MergeService mergeService, AiClient aiClient,
                        RuleBasedClassifier ruleBasedClassifier, ActivityLogService activityLogService,
                        BroadcastService broadcastService, DashboardService dashboardService,
                        PlatformTransactionManager transactionManager) {
        this.alertRepository = alertRepository;
        this.zoneRepository = zoneRepository;
        this.incidentService = incidentService;
        this.mergeService = mergeService;
        this.aiClient = aiClient;
        this.ruleBasedClassifier = ruleBasedClassifier;
        this.activityLogService = activityLogService;
        this.broadcastService = broadcastService;
        this.dashboardService = dashboardService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public List<AlertDTOOut> search(AlertStatus status, Long incidentId) {
        return alertRepository.search(status, incidentId).stream().map(DTOMapper::toDTO).toList();
    }

    // ---------------- pipeline ----------------
    // NOT @Transactional: each step commits on its own, and the AI call runs outside any transaction

    public AlertDTOOut ingest(AlertDTOIn dto) {
        return ingest(dto, null, null);
    }

    // Telegram bot: same pipeline + who sent it. Not in AlertDTOIn on purpose: POST /alerts is open
    // to external sources, and they must not be able to claim a trust level
    public AlertDTOOut ingest(AlertDTOIn dto, TelegramReporter reporter, ReporterTrust reporterTrust) {
        // 1) save raw alert as NEW + broadcast (live counter)
        Alert alert = transactionTemplate.execute(status -> saveNew(dto, reporter, reporterTrust));

        // 2) classify: AI, or rules on failure/timeout
        Classification classification = classify(alert);

        // 3) merge into an open incident or create a new one
        pipelineLock.lock();
        try {
            return transactionTemplate.execute(status -> process(alert.getId(), classification));
        } finally {
            pipelineLock.unlock();
        }
    }

    // used by /simulation/reset: no alert is half-processed while the tables are being cleared
    public void runExclusive(Runnable work) {
        pipelineLock.lock();
        try {
            work.run();
        } finally {
            pipelineLock.unlock();
        }
    }

    // processed in receivedAt order (stable: keeps request order otherwise)
    public List<AlertDTOOut> ingestBatch(List<AlertDTOIn> alerts) {
        return alerts.stream()
                .sorted(Comparator.comparing(AlertDTOIn::receivedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::ingest)
                .toList();
    }

    private Alert saveNew(AlertDTOIn dto, TelegramReporter reporter, ReporterTrust reporterTrust) {
        Alert alert = new Alert();
        alert.setSource(dto.source());
        alert.setSourceReference(dto.sourceReference());
        alert.setRawText(dto.text().trim());
        alert.setLanguage(hasText(dto.language())
                ? dto.language().trim().toLowerCase(Locale.ROOT)
                : detectLanguage(dto.text()));
        alert.setAttachmentUrl(dto.attachmentUrl());
        alert.setReceivedAt(dto.receivedAt() != null ? dto.receivedAt() : LocalDateTime.now());

        Zone zone = resolveZone(dto.zoneCode(), dto.x(), dto.y());
        alert.setZone(zone);

        // no position sent -> use the zone center so merging and the map still work
        boolean hasPosition = dto.x() != null && dto.y() != null;
        alert.setX(hasPosition ? dto.x() : zone == null ? null : zone.getCenterX());
        alert.setY(hasPosition ? dto.y() : zone == null ? null : zone.getCenterY());

        alert.setReporter(reporter);
        alert.setReporterTrust(reporterTrust);
        alert.setStatus(AlertStatus.NEW);
        alertRepository.save(alert);

        broadcastService.send(BroadcastService.ALERTS, "ALERT_RECEIVED", DTOMapper.toDTO(alert));
        return alert;
    }

    private AlertDTOOut process(Long alertId, Classification c) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ApiException("Alert not found"));

        // the operator dismissed it while it was being classified: keep it out
        if (alert.getStatus() == AlertStatus.DISMISSED) return DTOMapper.toDTO(alert);

        alert.setAiCategory(c.category());
        alert.setAiSeverity(c.severity());
        alert.setAiConfidence(c.confidence());
        alert.setProcessedAt(LocalDateTime.now());
        alert.setStatus(AlertStatus.PROCESSED);

        Incident incident = null;
        Optional<MergeService.Match> match = mergeService.findMatch(alert);
        if (match.isPresent()) {
            // lock + fresh state: the operator may have closed or changed it while we were scoring
            Incident target = incidentService.getIncidentForUpdate(match.get().incident().getId());
            if (!IncidentService.isClosed(target.getStatus())) {
                incident = incidentService.mergeAlert(target, alert, match.get().score(), match.get().decidedBy());
            }
        }
        if (incident == null) {
            incident = incidentService.createFromAlert(alert, c.summary(), c.decidedBy());
        }
        alertRepository.save(alert);

        // aiDecisions / fallbackDecisions in the stats are counted from this row's actorType
        activityLogService.log(EntityType.ALERT, alert.getId(), incident.getId(), ActionType.CREATED,
                c.decidedBy(), null, null, c.category() + "/" + c.severity(),
                String.format(Locale.ROOT, "Classified by %s (confidence %.2f)",
                        c.decidedBy() == ActorType.AI ? "AI" : "rules", c.confidence()));

        AlertDTOOut out = DTOMapper.toDTO(alert);
        broadcastService.send(BroadcastService.ALERTS, "ALERT_PROCESSED", out);
        dashboardService.broadcastStats(); // /topic/stats, after commit
        return out;
    }

    // ---------------- classification ----------------

    private Classification classify(Alert alert) {
        return aiClient.classify(alert)
                .flatMap(this::fromAi)
                .orElseGet(() -> ruleBasedClassifier.classify(alert.getRawText()));
    }

    private Optional<Classification> fromAi(AiClient.ClassifyResponse response) {
        IncidentCategory category = parseEnum(IncidentCategory.class, response.category());
        if (category == null) return Optional.empty(); // unusable -> rules

        Severity severity = parseEnum(Severity.class, response.severity());
        if (severity == null) {
            severity = (category == IncidentCategory.FIRE || category == IncidentCategory.MEDICAL)
                    ? Severity.HIGH
                    : RuleBasedClassifier.defaultSeverity(category);
        }

        double confidence = response.confidence() == null
                ? RuleBasedClassifier.FALLBACK_CONFIDENCE
                : Math.max(0, Math.min(1, response.confidence()));

        return Optional.of(new Classification(category, severity, confidence, response.summary(), ActorType.AI));
    }

    @Transactional(readOnly = true)
    public AlertDTOOut getById(Long alertId) {
        return DTOMapper.toDTO(alertRepository.findById(alertId).orElseThrow(() -> new ApiException("Alert not found")));
    }

    // operator: noise or wrongly merged -> DISMISSED and taken out of its incident (under the pipeline lock)
    public AlertDTOOut dismiss(Long alertId, String reason, Long actorId) {
        pipelineLock.lock();
        try {
            return transactionTemplate.execute(s -> {
                incidentService.validateOperator(actorId);
                Alert alert = alertRepository.findById(alertId).orElseThrow(() -> new ApiException("Alert not found"));
                if (alert.getStatus() == AlertStatus.DISMISSED) return DTOMapper.toDTO(alert);

                AlertStatus old = alert.getStatus();
                Incident incident = alert.getIncident();
                if (incident != null && IncidentService.isClosed(incident.getStatus())) {
                    throw new ApiException("Incident is already closed");
                }
                alert.setStatus(AlertStatus.DISMISSED);
                alert.setIncident(null);
                alertRepository.save(alert);
                if (incident != null) incidentService.detachAlert(incident.getId(), alertId, reason.trim(), actorId);

                activityLogService.log(EntityType.ALERT, alertId, incident == null ? null : incident.getId(),
                        ActionType.STATUS_CHANGED, ActorType.HUMAN, actorId, old.name(), AlertStatus.DISMISSED.name(),
                        "Dismissed: " + reason.trim());

                AlertDTOOut out = DTOMapper.toDTO(alert);
                broadcastService.send(BroadcastService.ALERTS, "ALERT_UPDATED", out);
                dashboardService.broadcastStats();
                return out;
            });
        } finally {
            pipelineLock.unlock();
        }
    }

    // ---------------- helpers ----------------

    private Zone resolveZone(String zoneCode, Double x, Double y) {
        if (hasText(zoneCode)) {
            Optional<Zone> byCode = zoneRepository.findByCode(zoneCode.trim().toUpperCase(Locale.ROOT));
            if (byCode.isPresent()) return byCode.get();
        }
        if (x == null || y == null) return null;

        return zoneRepository.findAll().stream()
                .filter(z -> z.getCenterX() != null && z.getCenterY() != null)
                .min(Comparator.comparingDouble(z -> AccessibilityService.distance(z.getCenterX(), z.getCenterY(), x, y)))
                .orElse(null);
    }

    private static String detectLanguage(String text) {
        return text.codePoints().anyMatch(cp -> Character.UnicodeBlock.of(cp) == Character.UnicodeBlock.ARABIC)
                ? "ar" : "en";
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        if (!hasText(value)) return null;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}