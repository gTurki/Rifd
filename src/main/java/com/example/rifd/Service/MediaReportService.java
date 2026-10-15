package com.example.rifd.Service;

import com.example.rifd.Api.ApiException;
import com.example.rifd.Entity.MediaFile;
import com.example.rifd.Repository.MediaFileRepository;
import com.example.rifd.Service.VisionClient.VisionResult;
import com.example.rifd.Telegram.GeminiGatekeeper;
import com.example.rifd.Telegram.GeminiGatekeeper.ImageInput;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.springframework.util.StringUtils.hasText;

// web reports (visitor page + staff app) with photos / a voice note: the same analysis as the Telegram bot.
// Files are stored first (the alert always keeps them), then: voice -> transcript (Gemini), photos -> description
// (Gemini) + fire/smoke detection (AI 2, in parallel). Any analysis failure -> a fallback line, never an error.
@Slf4j
@Service
public class MediaReportService {

    public static final String PREFIX = "up:";
    public static final int MAX_PHOTOS = 3;

    private static final long MAX_PHOTO_BYTES = 5L * 1024 * 1024;
    private static final long MAX_AUDIO_BYTES = 5L * 1024 * 1024;   // ~1 minute of 16 kHz mono WAV is ~2 MB
    private static final int MAX_TEXT_LENGTH = 2000;
    private static final long VISION_MAX_WAIT_SECONDS = 20;

    private static final Set<String> PHOTO_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> AUDIO_TYPES = Set.of("audio/wav", "audio/mpeg", "audio/mp4", "audio/aac", "audio/ogg");

    private static final String VOICE_LABEL = "التفريغ الصوتي: ";
    private static final String IMAGE_LABEL = "وصف الصور: ";
    private static final String VOICE_FAILED = "مرفق صوتي تعذّر تفريغه، الرجاء الرجوع للمرفق";
    private static final String IMAGE_FAILED = "صور مرفقة تعذّر تحليلها، الرجاء الرجوع للمرفقات";

    // text = the final alert text, attachmentUrl = "up:<voiceId>,<photoId>,..." (voice first) or null
    public record Result(String text, String attachmentUrl) {
    }

    private final MediaFileRepository mediaFileRepository;
    private final GeminiGatekeeper gemini;
    private final VisionClient visionClient;
    private final ExecutorService visionPool = Executors.newFixedThreadPool(2);

    public MediaReportService(MediaFileRepository mediaFileRepository, GeminiGatekeeper gemini, VisionClient visionClient) {
        this.mediaFileRepository = mediaFileRepository;
        this.gemini = gemini;
        this.visionClient = visionClient;
    }

    @PreDestroy
    public void shutdown() {
        visionPool.shutdownNow();
    }

    // baseText may be empty, but then a photo or a voice note is required
    public Result process(String baseText, List<MultipartFile> photos, MultipartFile audio, String requestId) {
        List<MultipartFile> photoFiles = photos == null ? List.of()
                : photos.stream().filter(f -> f != null && !f.isEmpty()).toList();
        boolean hasAudio = audio != null && !audio.isEmpty();

        if (photoFiles.size() > MAX_PHOTOS) throw new ApiException("You can attach up to " + MAX_PHOTOS + " photos");
        if (!hasText(baseText) && photoFiles.isEmpty() && !hasAudio) {
            throw new ApiException("Write a description, attach a photo or record a voice note");
        }

        // 1) validate everything before storing anything
        List<ImageInput> images = new ArrayList<>();
        for (MultipartFile f : photoFiles) {
            images.add(new ImageInput(read(f, MAX_PHOTO_BYTES, "Photo"), type(f, PHOTO_TYPES, "Photo")));
        }
        byte[] audioBytes = hasAudio ? read(audio, MAX_AUDIO_BYTES, "Voice note") : null;
        String audioType = hasAudio ? type(audio, AUDIO_TYPES, "Voice note") : null;

        // 2) store: the alert keeps its attachments even if every analysis fails
        List<Long> ids = new ArrayList<>();
        if (hasAudio) ids.add(mediaFileRepository.save(new MediaFile(audioBytes, audioType)).getId());
        for (ImageInput i : images) ids.add(mediaFileRepository.save(new MediaFile(i.bytes(), i.mimeType())).getId());
        String attachmentUrl = ids.isEmpty() ? null
                : PREFIX + ids.stream().map(String::valueOf).collect(Collectors.joining(","));

        // 3) analyze: AI 2 in the background while Gemini works
        CompletableFuture<Optional<VisionResult>> detection = detect(images, requestId);

        StringBuilder text = new StringBuilder(hasText(baseText) ? baseText.trim() : "");
        if (hasAudio) {
            Optional<String> transcript = gemini.transcribe(audioBytes, audioType);
            log.info("Web report {} voice: {}", requestId, transcript.map(t -> "'" + t + "'").orElse("not transcribed"));
            addLine(text, transcript.map(t -> VOICE_LABEL + t).orElse(VOICE_FAILED));
        }
        if (!images.isEmpty()) {
            Optional<String> description = gemini.describeImages(images);
            log.info("Web report {} photos: {}", requestId, description.map(d -> "'" + d + "'").orElse("not described"));
            addLine(text, description.map(d -> IMAGE_LABEL + d).orElse(IMAGE_FAILED));
        }

        String evidence = detection
                .completeOnTimeout(Optional.empty(), VISION_MAX_WAIT_SECONDS, TimeUnit.SECONDS)
                .join()
                .filter(VisionResult::hasFindings)
                .map(VisionResult::summaryAr)
                .orElse(null);

        // the detection line goes last and is never cut off (it carries "حريق"/"دخان" for the classifier, like Telegram)
        int room = evidence == null ? MAX_TEXT_LENGTH : MAX_TEXT_LENGTH - evidence.length() - 1;
        String body = text.length() > room ? text.substring(0, room) : text.toString();
        return new Result(evidence == null ? body : body + "\n" + evidence, attachmentUrl);
    }

    // AI 2: disabled / no photos / any failure -> empty (never "safe")
    private CompletableFuture<Optional<VisionResult>> detect(List<ImageInput> images, String requestId) {
        if (!visionClient.isEnabled() || images.isEmpty()) return CompletableFuture.completedFuture(Optional.empty());
        List<VisionClient.ImageData> data = images.stream()
                .map(i -> new VisionClient.ImageData(i.bytes(), i.mimeType()))
                .toList();
        try {
            return CompletableFuture
                    .supplyAsync(() -> visionClient.analyzeAll(data, requestId, "VISITOR_APP", Instant.now()), visionPool)
                    .exceptionally(e -> Optional.empty());
        } catch (RejectedExecutionException e) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

    private static byte[] read(MultipartFile file, long maxBytes, String what) {
        if (file.getSize() > maxBytes) throw new ApiException(what + " is too large (max " + (maxBytes / 1024 / 1024) + " MB)");
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ApiException(what + " could not be read");
        }
    }

    // browsers name WAV in several ways: normalized to audio/wav (what Gemini expects)
    private static String type(MultipartFile file, Set<String> allowed, String what) {
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        int semicolon = type.indexOf(';');
        if (semicolon >= 0) type = type.substring(0, semicolon).trim();
        if (type.equals("audio/x-wav") || type.equals("audio/wave") || type.equals("audio/vnd.wave")) type = "audio/wav";
        if (type.equals("image/jpg")) type = "image/jpeg";
        if (!allowed.contains(type)) throw new ApiException(what + " type is not supported: " + type);
        return type;
    }

    private static void addLine(StringBuilder text, String line) {
        if (!text.isEmpty()) text.append('\n');
        text.append(line);
    }
}