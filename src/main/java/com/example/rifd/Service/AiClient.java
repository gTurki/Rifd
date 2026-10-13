package com.example.rifd.Service;

import com.example.rifd.Entity.Alert;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class AiClient {

    // ---- responses ----
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ClassifyResponse(String severity, String category, Double confidence, String summary) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MergeScoreResponse(Long bestIncidentId, Double score) {
    }

    // ---- requests ----
    public record ClassifyRequest(Long alertId, String text, String language, String source,
                                  Double x, Double y, String zoneCode, LocalDateTime receivedAt) {
    }

    public record MergeAlert(Long alertId, String text, String category, Double x, Double y,
                             LocalDateTime receivedAt) {
    }

    public record MergeCandidate(Long incidentId, String title, String summary, String category,
                                 Double x, Double y, LocalDateTime lastAlertAt) {
    }

    public record MergeScoreRequest(MergeAlert alert, List<MergeCandidate> candidates) {
    }

    private final boolean enabled;
    private final RestClient restClient;

    public AiClient(@Value("${ai.enabled:true}") boolean enabled,
                    @Value("${ai.base-url:http://localhost:8000}") String baseUrl,
                    @Value("${ai.timeout-ms:3000}") long timeoutMs) {
        this.enabled = enabled;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public boolean isEnabled() {
        return enabled;
    }

    // empty = AI disabled / failed / timed out -> caller uses the rule-based fallback
    public Optional<ClassifyResponse> classify(Alert alert) {
        if (!enabled) return Optional.empty();
        try {
            ClassifyRequest request = new ClassifyRequest(alert.getId(), alert.getRawText(), alert.getLanguage(),
                    alert.getSource() == null ? null : alert.getSource().name(),
                    alert.getX(), alert.getY(),
                    alert.getZone() == null ? null : alert.getZone().getCode(),
                    alert.getReceivedAt());

            return Optional.ofNullable(restClient.post()
                    .uri("/classify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ClassifyResponse.class));
        } catch (Exception e) {
            log.warn("AI classify failed for alert #{}, using rules: {}", alert.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<MergeScoreResponse> mergeScore(MergeAlert alert, List<MergeCandidate> candidates) {
        if (!enabled) return Optional.empty();
        try {
            return Optional.ofNullable(restClient.post()
                    .uri("/merge-score")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new MergeScoreRequest(alert, candidates))
                    .retrieve()
                    .body(MergeScoreResponse.class));
        } catch (Exception e) {
            log.warn("AI merge-score failed for alert #{}, using rules: {}", alert.alertId(), e.getMessage());
            return Optional.empty();
        }
    }
}