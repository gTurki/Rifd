package com.example.rifd.Service;

import com.example.rifd.Entity.Alert;
import com.example.rifd.Entity.Incident;
import com.example.rifd.Enum.ActorType;
import com.example.rifd.Enum.AlertStatus;
import com.example.rifd.Enum.IncidentStatus;
import com.example.rifd.Repository.AlertRepository;
import com.example.rifd.Repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MergeService {

    public record Match(Incident incident, double score, ActorType decidedBy) {
    }

    private record Candidate(Incident incident, double distance, double minutesSinceLast, LocalDateTime lastAlertAt) {
    }

    private static final List<IncidentStatus> CLOSED = List.of(IncidentStatus.RESOLVED, IncidentStatus.FALSE_ALARM);

    // limits for the request sent to the AI with each alert
    private static final int MAX_AI_INCIDENTS = 10;
    private static final int MAX_AI_TEXTS = 10;

    private final IncidentRepository incidentRepository;
    private final AlertRepository alertRepository;

    @Value("${merge.threshold:0.6}")
    private double threshold;

    @Value("${merge.max-distance:80}")
    private double maxDistance;

    @Value("${merge.time-window-minutes:15}")
    private double timeWindowMinutes;

    // true: in the rule fallback, merge with the best candidate (candidates are already same category,
    // close in space and time). Arabic vs English text barely overlaps, so a text threshold would split them.
    @Value("${merge.fallback-always-merge:true}")
    private boolean fallbackAlwaysMerge;

    // rule-based merge (AI failed, or an incident opened after the AI call). expects alert.aiCategory to be set
    public Optional<Match> findMatch(Alert alert) {
        return findMatch(alert, Set.of());
    }

    // excludedIds = incidents the AI already saw and decided about
    public Optional<Match> findMatch(Alert alert, Set<Long> excludedIds) {
        if (alert.getAiCategory() == null) return Optional.empty();

        List<Candidate> candidates = findCandidates(alert).stream()
                .filter(c -> !excludedIds.contains(c.incident().getId()))
                .toList();
        if (candidates.isEmpty()) return Optional.empty();
        return fallback(alert, candidates);
    }

    // open incidents sent to the AI with the alert: any category (the model classifies the alert itself),
    // within merge.max-distance and the time window, nearest first. Must run inside a transaction (lazy zone).
    public List<AiClient.OpenIncident> openIncidentsForAi(Alert alert) {
        if (alert.getX() == null || alert.getY() == null || alert.getReceivedAt() == null) return List.of();
        List<AiClient.OpenIncident> result = new ArrayList<>();

        List<Incident> open = incidentRepository.findAll().stream()
                .filter(i -> !CLOSED.contains(i.getStatus()))
                .filter(i -> i.getAlertCount() != null && i.getAlertCount() > 0)
                .filter(i -> i.getX() != null && i.getY() != null && i.getZone() != null && i.getCategory() != null)
                .sorted(Comparator.comparingDouble(i -> AccessibilityService.distance(alert.getX(), alert.getY(), i.getX(), i.getY())))
                .toList();

        for (Incident incident : open) {
            if (result.size() >= MAX_AI_INCIDENTS) break;
            double distance = AccessibilityService.distance(alert.getX(), alert.getY(), incident.getX(), incident.getY());
            if (distance > maxDistance) break; // sorted: the rest is farther

            List<Alert> alerts = alertRepository.findByIncidentIdOrderByReceivedAtAsc(incident.getId()).stream()
                    .filter(a -> a.getStatus() != AlertStatus.DISMISSED)
                    .toList();
            if (alerts.isEmpty()) continue;

            LocalDateTime last = alerts.get(alerts.size() - 1).getReceivedAt();
            if (last == null) continue;
            double minutes = Math.abs(Duration.between(last, alert.getReceivedAt()).toMillis()) / 60000.0;
            if (minutes > timeWindowMinutes) continue;

            // the latest texts are enough to compare with
            List<String> texts = alerts.subList(Math.max(0, alerts.size() - MAX_AI_TEXTS), alerts.size()).stream()
                    .map(Alert::getRawText)
                    .toList();

            result.add(new AiClient.OpenIncident(incident.getId(), texts, incident.getX(), incident.getY(),
                    incident.getZone().getCode(), incident.getCategory().name(), last));
        }
        return result;
    }

    private List<Candidate> findCandidates(Alert alert) {
        List<Candidate> result = new ArrayList<>();
        for (Incident incident : incidentRepository.findByCategoryAndStatusNotIn(alert.getAiCategory(), CLOSED)) {
            // every alert was dismissed: nothing real left to merge into
            if (Integer.valueOf(0).equals(incident.getAlertCount())) continue;
            Double distance = distance(alert, incident);
            if (distance == null || distance > maxDistance) continue;

            LocalDateTime last = alertRepository.findLastReceivedAt(incident.getId());
            if (last == null) last = incident.getCreatedAt();
            if (last == null) continue;

            double minutes = Math.abs(Duration.between(last, alert.getReceivedAt()).toMillis()) / 60000.0;
            if (minutes > timeWindowMinutes) continue;

            result.add(new Candidate(incident, distance, minutes, last));
        }
        return result;
    }

    private Optional<Match> fallback(Alert alert, List<Candidate> candidates) {
        Set<String> alertWords = words(alert.getRawText());

        return candidates.stream()
                .map(c -> new Match(c.incident(), round(ruleScore(alertWords, c)), ActorType.SYSTEM))
                .max(Comparator.comparingDouble(Match::score))
                .filter(m -> fallbackAlwaysMerge || m.score() >= threshold);
    }

    // score = 0.5 * text + 0.3 * proximity + 0.2 * recency
    private double ruleScore(Set<String> alertWords, Candidate c) {
        Incident incident = c.incident();
        String incidentText = Objects.toString(incident.getTitle(), "") + " " + Objects.toString(incident.getSummary(), "");

        double text = jaccard(alertWords, words(incidentText));
        double proximity = maxDistance <= 0 ? 0 : 1 - (c.distance() / maxDistance);
        double recency = timeWindowMinutes <= 0 ? 0 : 1 - (c.minutesSinceLast() / timeWindowMinutes);

        return clamp(0.5 * text + 0.3 * clamp(proximity) + 0.2 * clamp(recency));
    }

    private Double distance(Alert alert, Incident incident) {
        if (alert.getX() != null && alert.getY() != null && incident.getX() != null && incident.getY() != null) {
            return AccessibilityService.distance(alert.getX(), alert.getY(), incident.getX(), incident.getY());
        }
        if (alert.getZone() != null && incident.getZone() != null
                && Objects.equals(alert.getZone().getId(), incident.getZone().getId())) {
            return 0.0; // no coordinates, same zone
        }
        return null;
    }

    private static Set<String> words(String text) {
        return RuleBasedClassifier.tokenize(text).stream()
                .filter(t -> t.length() >= 2)
                .collect(Collectors.toSet());
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) intersection.size() / union.size();
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}