package com.example.rifd.Service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

// AI 2 (ExpoGuard): POST /v1/analyze/image = fire / smoke detection on one photo.
// Rules from its README: HTTP 200 is not "danger", OK with no detections is "nothing seen" (not "safe"),
// UNUSABLE = image quality too low, ERROR / timeout NEVER means "no danger" -> Optional.empty(), the caller goes on without it.
@Slf4j
@Service
public class VisionClient {

    public static final String OK = "OK";
    public static final String UNUSABLE = "UNUSABLE";
    public static final String ERROR = "ERROR";

    // only these labels are used; anything else (e.g. crowd counts) is ignored
    private static final Map<String, String> LABELS_AR = Map.of(
            "visible_flame", "لهب ظاهر",
            "suspected_smoke", "دخان محتمل");

    // ---- response (UnifiedAnalysis v2), only the fields we read ----
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Detection(String detectionId, String label, Double confidence, String evidence) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ImageQuality(Boolean usable, List<String> issues) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Analysis(String analysisStatus, String modelVersion, ImageQuality imageQuality,
                           List<Detection> detections) {
    }

    // ---- what the rest of the app sees ----
    public record ImageData(byte[] bytes, String mimeType) {
    }

    public record Finding(String label, double confidence) {
        public String labelAr() {
            return LABELS_AR.getOrDefault(label, label);
        }
    }

    // status = OK (at least one photo analyzed) or UNUSABLE (all photos too poor). findings = best score per label
    public record VisionResult(String status, List<Finding> findings, String modelVersion) {
        public boolean hasFindings() {
            return findings != null && !findings.isEmpty();
        }

        // e.g. "كشف آلي (حريق/دخان): لهب ظاهر (ثقة 0.72)، دخان محتمل (ثقة 0.58)"
        public String summaryAr() {
            if (!hasFindings()) return null;
            return "كشف آلي (حريق/دخان): " + findings.stream()
                    .map(f -> f.labelAr() + " (ثقة " + String.format(Locale.ROOT, "%.2f", f.confidence()) + ")")
                    .collect(Collectors.joining("، "));
        }
    }

    private final boolean enabled;
    private final double minConfidence;
    private final RestClient restClient;
    private final RestClient warmupClient;

    public VisionClient(@Value("${vision-ai.enabled:false}") boolean enabled,
                        @Value("${vision-ai.base-url:http://127.0.0.1:8000}") String baseUrl,
                        @Value("${vision-ai.timeout-ms:10000}") long timeoutMs,
                        @Value("${vision-ai.warmup-timeout-ms:120000}") long warmupTimeoutMs,
                        @Value("${vision-ai.min-confidence:0.45}") double minConfidence,
                        @Value("${vision-ai.token:}") String token) {
        this.enabled = enabled;
        this.minConfidence = minConfidence;
        this.restClient = client(baseUrl, timeoutMs, token);
        this.warmupClient = client(baseUrl, warmupTimeoutMs, token);
    }

    // the Space is private: every call (analyze + warm-up) carries the Hugging Face token
    private static RestClient client(String baseUrl, long timeoutMs, String token) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));
        RestClient.Builder builder = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory);
        if (token != null && !token.isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return builder.build();
    }

    public boolean isEnabled() {
        return enabled;
    }

    // one call per photo (in order). Empty = nothing usable came back (disabled, all failed, or all ERROR)
    public Optional<VisionResult> analyzeAll(List<ImageData> images, String requestId, String source, Instant capturedAt) {
        if (!enabled || images == null || images.isEmpty()) return Optional.empty();

        Map<String, Double> best = new HashMap<>();
        int ok = 0;
        int unusable = 0;
        String modelVersion = null;

        for (int i = 0; i < images.size(); i++) {
            Optional<Analysis> analysis = analyze(images.get(i), requestId, requestId + "-img" + (i + 1), source, capturedAt);
            if (analysis.isEmpty()) continue;

            Analysis a = analysis.get();
            modelVersion = a.modelVersion();
            if (UNUSABLE.equals(a.analysisStatus())) {
                unusable++;
                log.info("Vision {} photo {}: UNUSABLE {}", requestId, i + 1,
                        a.imageQuality() == null ? "" : a.imageQuality().issues());
                continue;
            }
            if (!OK.equals(a.analysisStatus())) continue;   // ERROR (or unknown) = no information

            ok++;
            if (a.detections() == null) continue;
            for (Detection d : a.detections()) {
                if (d.label() == null || !LABELS_AR.containsKey(d.label())) continue;
                if (d.confidence() == null || d.confidence() < minConfidence) continue;
                best.merge(d.label(), d.confidence(), Math::max);
            }
        }

        if (ok == 0 && unusable == 0) return Optional.empty();

        List<Finding> findings = new ArrayList<>();
        best.forEach((label, confidence) -> findings.add(new Finding(label, confidence)));
        findings.sort(Comparator.comparingDouble(Finding::confidence).reversed());

        VisionResult result = new VisionResult(ok > 0 ? OK : UNUSABLE, findings, modelVersion);
        log.info("Vision {}: status {}, {}", requestId, result.status(),
                result.hasFindings() ? result.summaryAr() : "no fire/smoke detected");
        return Optional.of(result);
    }

    private Optional<Analysis> analyze(ImageData image, String requestId, String imageId, String source, Instant capturedAt) {
        try {
            MediaType type = mediaType(image.mimeType());
            String filename = MediaType.IMAGE_PNG.equals(type) ? "photo.png" : "photo.jpg";

            // plain multipart (no MultipartBodyBuilder: it needs reactive-streams, which is not on the classpath)
            ByteArrayResource file = new ByteArrayResource(image.bytes()) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };
            HttpHeaders fileHeaders = new HttpHeaders();
            fileHeaders.setContentType(type);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new HttpEntity<>(file, fileHeaders));
            body.add("requestId", requestId);
            body.add("imageId", imageId);
            body.add("source", source);
            body.add("capturedAt", (capturedAt == null ? Instant.now() : capturedAt).toString());

            Analysis analysis = restClient.post()
                    .uri("/v1/analyze/image")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(Analysis.class);

            if (analysis == null || ERROR.equals(analysis.analysisStatus())) {
                log.warn("Vision {} returned ERROR / empty: ignored (not treated as safe)", imageId);
                return Optional.empty();
            }
            return Optional.of(analysis);
        } catch (Exception | LinkageError e) {
            log.warn("Vision analyze failed for {} (ignored, not treated as safe): {}", imageId, e.toString());
            return Optional.empty();
        }
    }

    private static MediaType mediaType(String mimeType) {
        try {
            return mimeType == null ? MediaType.IMAGE_JPEG : MediaType.parseMediaType(mimeType);
        } catch (Exception e) {
            return MediaType.IMAGE_JPEG;
        }
    }

    // a free Hugging Face Space sleeps when idle: wake it up at startup, in the background
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        if (!enabled) return;
        Thread thread = new Thread(() -> {
            try {
                String health = warmupClient.get().uri("/health").retrieve().body(String.class);
                log.info("Vision AI service is awake: {}", health);
            } catch (Exception e) {
                log.warn("Vision AI warm-up failed (photos go on without detection until it answers): {}", e.getMessage());
            }
        }, "vision-warmup");
        thread.setDaemon(true);
        thread.start();
    }
}