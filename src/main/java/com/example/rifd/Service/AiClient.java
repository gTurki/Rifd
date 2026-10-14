package com.example.rifd.Service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// AI 1 (Hugging Face Space): POST /process-alert = classification + merge decision in one call.
// Any failure / timeout -> Optional.empty() -> the caller uses the rule-based fallback.
@Slf4j
@Service
public class AiClient {

    // ---- POST /process-alert ----
    public static final String MERGED = "MERGED";
    public static final String NEEDS_REVIEW = "NEEDS_REVIEW";
    public static final String PROCESSED = "PROCESSED";

    // x, y (meters), zoneCode and receivedAt are required by the model
    public record AlertIn(Long alertId, String rawText, Double x, Double y, String zoneCode,
                          LocalDateTime receivedAt) {
    }

    public record OpenIncident(Long incidentId, List<String> alertTexts, Double x, Double y, String zoneCode,
                               String category, LocalDateTime lastAlertAt) {
    }

    public record ProcessAlertRequest(AlertIn alert, List<OpenIncident> openIncidents) {
    }

    // suggestedAction / requiredSpecialty / ... are ignored on purpose: PlaybookService decides (deterministic)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProcessAlertResponse(Long alertId, String aiCategory, String aiSeverity, Double aiConfidence,
                                       String status, Long incident, Long suggestedIncident, Double mergeScore) {
    }

    // ---- kept for RecommendationService / AccessibilityService (the Space has no such endpoints) ----
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RecommendResponse(Long playbookId, String actionText, String reasoning, Double confidence) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RouteMessageResponse(String ar, String en) {
    }

    public record RecommendIncident(Long id, String title, String summary, String category, String severity) {
    }

    public record RecommendPlaybook(Long id, String title, String steps) {
    }

    public record RouteMessageRequest(String brokenAsset, String fromZone, String toZone, String alternativeName) {
    }

    private final boolean enabled;
    private final RestClient restClient;
    private final RestClient warmupClient;

    public AiClient(@Value("${ai.enabled:true}") boolean enabled,
                    @Value("${ai.base-url:http://localhost:8000}") String baseUrl,
                    @Value("${ai.timeout-ms:8000}") long timeoutMs,
                    @Value("${ai.warmup-timeout-ms:90000}") long warmupTimeoutMs) {
        this.enabled = enabled;
        this.restClient = client(baseUrl, timeoutMs);
        this.warmupClient = client(baseUrl, warmupTimeoutMs);
    }

    private static RestClient client(String baseUrl, long timeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Optional<ProcessAlertResponse> processAlert(ProcessAlertRequest request) {
        if (!enabled) return Optional.empty();
        try {
            return Optional.ofNullable(restClient.post()
                    .uri("/process-alert")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ProcessAlertResponse.class));
        } catch (Exception e) {
            log.warn("AI process-alert failed for alert #{}, using rules: {}", request.alert().alertId(), e.getMessage());
            return Optional.empty();
        }
    }

    // not provided by the Space: the playbook recommendation stays deterministic
    public Optional<RecommendResponse> recommend(RecommendIncident incident, List<RecommendPlaybook> playbooks) {
        return Optional.empty();
    }

    // not provided by the Space: the Arabic/English template message is used
    public Optional<RouteMessageResponse> routeMessage(RouteMessageRequest request) {
        return Optional.empty();
    }

    // the free Space sleeps when idle (cold start = tens of seconds): wake it up at startup,
    // in the background, so the first real alerts do not time out
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        if (!enabled) return;
        Thread thread = new Thread(() -> {
            try {
                warmupClient.get().uri("/health").retrieve().toBodilessEntity();
                log.info("AI service is awake");
            } catch (Exception e) {
                log.warn("AI service warm-up failed (rules will be used until it answers): {}", e.getMessage());
            }
        }, "ai-warmup");
        thread.setDaemon(true);
        thread.start();
    }
}