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
import com.example.rifd.Repository.VisitorRepository;
import com.example.rifd.Repository.ZoneRepository;
import com.example.rifd.Service.RuleBasedClassifier.Classification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static org.springframework.util.StringUtils.hasText;

@Service
public class AlertService {

    private final AlertRepository alertRepository;
    private final VisitorRepository visitorRepository;
    private final ZoneRepository zoneRepository;
    private final IncidentService incidentService;
    private final MergeService mergeService;
    private final AiClient aiClient;
    private final RuleBasedClassifier ruleBasedClassifier;
    private final ActivityLogService activityLogService;
    private final BroadcastService broadcastService;
    private final DashboardService dashboardService;
    private final TransactionTemplate transactionTemplate;

    // an alert of these categories is never merged by the AI into an incident of another category:
    // it needs its own team (a vague OTHER / CLEANLINESS label inside a known incident still merges)
    private static final Set<IncidentCategory> NEEDS_OWN_TEAM = EnumSet.of(
            IncidentCategory.MEDICAL, IncidentCategory.FIRE, IncidentCategory.SECURITY, IncidentCategory.LOST_PERSON);

    // below this the AI category/severity is not trusted (the rules classify); its merge decision is still used
    @Value("${ai.min-confidence:0.5}")
    private double minConfidence;

    // merge/create runs one alert at a time so the same event never opens two incidents
    private final ReentrantLock pipelineLock = new ReentrantLock(true);

    public AlertService(AlertRepository alertRepository, ZoneRepository zoneRepository,
                        IncidentService incidentService, MergeService mergeService, AiClient aiClient,
                        RuleBasedClassifier ruleBasedClassifier, ActivityLogService activityLogService,
                        BroadcastService broadcastService, DashboardService dashboardService,
                        VisitorRepository visitorRepository, PlatformTransactionManager transactionManager) {
        this.alertRepository = alertRepository;
        this.visitorRepository = visitorRepository;
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
        return ingest(dto, reporter, reporterTrust, null);
    }

    // visitor page: the report belongs to the signed-in visitor ("my reports")
    public AlertDTOOut ingestForVisitor(AlertDTOIn dto, Long visitorId) {
        return ingest(dto, null, null, visitorId);
    }

    private AlertDTOOut ingest(AlertDTOIn dto, TelegramReporter reporter, ReporterTrust reporterTrust, Long visitorId) {
        // 1) save raw alert as NEW + broadcast (live counter)
        Alert alert = transactionTemplate.execute(status -> saveNew(dto, reporter, reporterTrust, visitorId));

        // 2) AI: classification + merge decision in one call (outside any transaction and lock).
        //    Rules on failure / timeout
        Analysis analysis = analyze(alert);

        // 3) merge into an open incident or create a new one
        pipelineLock.lock();
        try {
            return transactionTemplate.execute(status -> process(alert.getId(), analysis));
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

    private Alert saveNew(AlertDTOIn dto, TelegramReporter reporter, ReporterTrust reporterTrust, Long visitorId) {
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
        alert.setVisitor(visitorId == null ? null : visitorRepository.getReferenceById(visitorId));
        alert.setStatus(AlertStatus.NEW);
        alertRepository.save(alert);

        broadcastService.send(BroadcastService.ALERTS, "ALERT_RECEIVED", DTOMapper.toDTO(alert));
        return alert;
    }

    private AlertDTOOut process(Long alertId, Analysis analysis) {
        Classification c = analysis.classification();
        AiDecision d = analysis.decision();

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
        Long possibleDuplicateOf = null;
        String note = "";

        if (d == null) {
            // no usable AI merge decision -> rules
            incident = mergeByRules(alert, Set.of());
        } else if (AiClient.MERGED.equals(d.status())) {
            // lock + fresh state: the operator may have closed or changed it while the AI was working
            Incident target = incidentService.getIncidentForUpdate(d.incidentId());
            if (!IncidentService.isClosed(target.getStatus())) {
                if (target.getCategory() == c.category() || !NEEDS_OWN_TEAM.contains(c.category())) {
                    incident = incidentService.mergeAlert(target, alert, d.score(), ActorType.AI);
                } else {
                    // same place and time but an urgent other category (e.g. MEDICAL inside a CROWD incident):
                    // merging would send the wrong team. New incident (its own playbook) + "possible duplicate" flag
                    possibleDuplicateOf = target.getId();
                    note = " | AI merge into " + target.getCode() + " refused: category " + c.category()
                            + " differs from " + target.getCategory();
                }
            }
        } else {
            // PROCESSED / NEEDS_REVIEW: the AI saw no sure match. An incident opened AFTER the AI call
            // (another alert at the same moment) was not shown to the model -> rules on those only.
            // Exception: the model said PROCESSED but did not understand the alert (confidence below
            // ai.min-confidence, the rules classified it) -> its "new incident" is not trusted either:
            // the rules check every open incident (same category, distance and time window)
            // Also NEEDS_REVIEW (the model is unsure): the rules settle it. Same category + distance + time window
            // = the same incident -> merge. No rules match (e.g. another category) -> new incident + duplicate flag
            boolean unsureNew = analysis.lowAiConfidence() != null && AiClient.PROCESSED.equals(d.status());
            boolean review = AiClient.NEEDS_REVIEW.equals(d.status());
            incident = mergeByRules(alert, unsureNew || review ? Set.of() : d.sentIncidentIds());
            if (incident != null && d.sentIncidentIds().contains(incident.getId())) {
                if (unsureNew) {
                    note = String.format(Locale.ROOT,
                            " | AI said PROCESSED with low confidence %.2f: merged by rules into %s",
                            analysis.lowAiConfidence(), incident.getCode());
                } else if (review) {
                    note = String.format(Locale.ROOT,
                            " | AI asked for review (score %s): same category nearby, merged by rules into %s",
                            d.score() == null ? "?" : String.format(Locale.ROOT, "%.2f", d.score()), incident.getCode());
                }
            }
            if (incident == null && AiClient.NEEDS_REVIEW.equals(d.status())) {
                possibleDuplicateOf = d.incidentId();
            }
        }

        if (incident == null) {
            incident = incidentService.createFromAlert(alert, c.summary(), c.decidedBy());
            if (possibleDuplicateOf != null) {
                Incident original = incidentService.getIncidentForUpdate(possibleDuplicateOf);
                if (!IncidentService.isClosed(original.getStatus())) {
                    incidentService.flagPossibleDuplicate(incident, original, d.score());
                }
            }
        }
        alertRepository.save(alert);

        // aiDecisions / fallbackDecisions in the stats are counted from this row's actorType
        activityLogService.log(EntityType.ALERT, alert.getId(), incident.getId(), ActionType.CREATED,
                c.decidedBy(), null, null, c.category() + "/" + c.severity(),
                String.format(Locale.ROOT, "Classified by %s (confidence %.2f)%s%s%s",
                        c.decidedBy() == ActorType.AI ? "AI" : "rules", c.confidence(),
                        analysis.lowAiConfidence() == null ? "" : String.format(Locale.ROOT,
                                " | AI confidence %.2f below %.2f: rules used", analysis.lowAiConfidence(), minConfidence),
                        d == null ? "" : " | AI merge decision: " + d.status(), note));

        AlertDTOOut out = DTOMapper.toDTO(alert);
        broadcastService.send(BroadcastService.ALERTS, "ALERT_PROCESSED", out);
        dashboardService.broadcastStats(); // /topic/stats, after commit
        return out;
    }

    private Incident mergeByRules(Alert alert, Set<Long> excludedIds) {
        Optional<MergeService.Match> match = mergeService.findMatch(alert, excludedIds);
        if (match.isEmpty()) return null;
        Incident target = incidentService.getIncidentForUpdate(match.get().incident().getId());
        if (IncidentService.isClosed(target.getStatus())) return null;
        return incidentService.mergeAlert(target, alert, match.get().score(), match.get().decidedBy());
    }

    // ---------------- AI (classification + merge decision) ----------------

    // decision == null -> merge by rules. sentIncidentIds = the open incidents the model compared with
    private record AiDecision(String status, Long incidentId, Double score, Set<Long> sentIncidentIds) {
    }

    // lowAiConfidence = the model answered but was unsure of the category -> the rules classified the alert
    private record Analysis(Classification classification, AiDecision decision, Double lowAiConfidence) {
    }

    private Analysis analyze(Alert saved) {
        if (aiClient.isEnabled()) {
            AiClient.ProcessAlertRequest request = transactionTemplate.execute(s -> buildAiRequest(saved.getId()));
            if (request != null) {
                Optional<Analysis> fromAi = aiClient.processAlert(request)
                        .flatMap(r -> fromAi(r, request, saved.getRawText()));
                if (fromAi.isPresent()) return fromAi.get();
            }
        }
        return new Analysis(ruleBasedClassifier.classify(saved.getRawText()), null, null);
    }

    // null = cannot ask the model (it needs a position and a zone) -> rules
    private AiClient.ProcessAlertRequest buildAiRequest(Long alertId) {
        Alert alert = alertRepository.findById(alertId).orElse(null);
        if (alert == null || alert.getX() == null || alert.getY() == null
                || alert.getZone() == null || alert.getReceivedAt() == null) {
            return null;
        }
        AiClient.AlertIn in = new AiClient.AlertIn(alert.getId(), alert.getRawText(), alert.getX(), alert.getY(),
                alert.getZone().getCode(), alert.getReceivedAt());
        return new AiClient.ProcessAlertRequest(in, mergeService.openIncidentsForAi(alert));
    }

    private Optional<Analysis> fromAi(AiClient.ProcessAlertResponse response, AiClient.ProcessAlertRequest request,
                                      String rawText) {
        IncidentCategory category = parseEnum(IncidentCategory.class, response.aiCategory());
        if (category == null) return Optional.empty(); // unusable -> rules for everything

        Severity severity = parseEnum(Severity.class, response.aiSeverity());
        if (severity == null) {
            severity = (category == IncidentCategory.FIRE || category == IncidentCategory.MEDICAL)
                    ? Severity.HIGH
                    : RuleBasedClassifier.defaultSeverity(category);
        }

        double confidence = response.aiConfidence() == null
                ? RuleBasedClassifier.FALLBACK_CONFIDENCE
                : Math.max(0, Math.min(1, response.aiConfidence()));
        Classification classification = new Classification(category, severity, confidence, null, ActorType.AI);

        Set<Long> sent = request.openIncidents().stream()
                .map(AiClient.OpenIncident::incidentId)
                .collect(Collectors.toSet());
        String status = response.status() == null ? "" : response.status().trim().toUpperCase(Locale.ROOT);
        Double score = response.mergeScore() == null ? null : Math.round(response.mergeScore() * 100) / 100.0;

        AiDecision decision = switch (status) {
            // an id we did not send = unusable -> rules decide the merge
            case AiClient.MERGED -> sent.contains(response.incident())
                    ? new AiDecision(AiClient.MERGED, response.incident(), score == null ? 0.0 : score, sent)
                    : null;
            case AiClient.NEEDS_REVIEW -> sent.contains(response.suggestedIncident())
                    ? new AiDecision(AiClient.NEEDS_REVIEW, response.suggestedIncident(), score, sent)
                    : new AiDecision(AiClient.PROCESSED, null, null, sent);
            case AiClient.PROCESSED -> new AiDecision(AiClient.PROCESSED, null, null, sent);
            default -> null;
        };
        // unsure model (below ai.min-confidence): the rules classify, the AI merge decision is kept
        if (confidence < minConfidence) {
            return Optional.of(new Analysis(ruleBasedClassifier.classify(rawText), decision, confidence));
        }
        return Optional.of(new Analysis(classification, decision, null));
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