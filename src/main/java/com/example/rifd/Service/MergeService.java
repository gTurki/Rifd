package com.example.rifd.Service;

import com.example.rifd.Entity.Alert;
import com.example.rifd.Entity.Incident;
import com.example.rifd.Enum.ActorType;
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

    private final IncidentRepository incidentRepository;
    private final AlertRepository alertRepository;
    private final AiClient aiClient;

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

    // expects alert.aiCategory to be set already
    public Optional<Match> findMatch(Alert alert) {
        if (alert.getAiCategory() == null) return Optional.empty();

        List<Candidate> candidates = findCandidates(alert);
        if (candidates.isEmpty()) return Optional.empty();

        Optional<AiClient.MergeScoreResponse> ai = aiClient.mergeScore(toAiAlert(alert),
                candidates.stream().map(this::toAiCandidate).toList());

        if (ai.isPresent()) {
            Optional<Match> fromAi = fromAi(ai.get(), candidates);
            if (fromAi != null) return fromAi;
            // AI answer unusable -> rules
        }
        return fallback(alert, candidates);
    }

    private List<Candidate> findCandidates(Alert alert) {
        List<Candidate> result = new ArrayList<>();
        for (Incident incident : incidentRepository.findByCategoryAndStatusNotIn(alert.getAiCategory(), CLOSED)) {
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

    // null = AI response unusable; empty = AI decided "new incident"
    private Optional<Match> fromAi(AiClient.MergeScoreResponse response, List<Candidate> candidates) {
        if (response.score() == null) return null;
        if (response.bestIncidentId() == null) return Optional.empty();

        Optional<Candidate> best = candidates.stream()
                .filter(c -> c.incident().getId().equals(response.bestIncidentId()))
                .findFirst();
        if (best.isEmpty()) return null;

        double score = round(response.score());
        return score >= threshold
                ? Optional.of(new Match(best.get().incident(), score, ActorType.AI))
                : Optional.empty();
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

    private AiClient.MergeAlert toAiAlert(Alert alert) {
        return new AiClient.MergeAlert(alert.getId(), alert.getRawText(), alert.getAiCategory().name(),
                alert.getX(), alert.getY(), alert.getReceivedAt());
    }

    private AiClient.MergeCandidate toAiCandidate(Candidate c) {
        Incident i = c.incident();
        return new AiClient.MergeCandidate(i.getId(), i.getTitle(), i.getSummary(),
                i.getCategory() == null ? null : i.getCategory().name(), i.getX(), i.getY(), c.lastAlertAt());
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