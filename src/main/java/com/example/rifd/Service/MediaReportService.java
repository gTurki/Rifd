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
// voice -> transcript (Gemini), photos -> description (Gemini) + fire/smoke detection (AI 2, in parallel), then the
// files are stored (the alert always keeps them). Any analysis failure -> a fallback line, never an error; the only
// rejection is a voice note with nobody speaking in it (too short, or Gemini answered and heard no speech).
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

    // 16 kHz mono 16-bit WAV (what the web recorder sends) = 32 000 bytes per second: less than ~0.5 s is not a message
    private static final long MIN_WAV_BYTES = 16_000;
    private static final String NO_VOICE = "ما قدرنا نسمع شي في التسجيل، سجّل مرة ثانية أو احذف التسجيل واكتب وش صار";

    // text = the final alert text, attachmentUrl = "up:<voiceId>,<photoId>,..." (voice first) or null,
    // transcript = what Gemini heard in the voice note (null: no voice note or not transcribed), shown back to the sender
    public record Result(String text, String attachmentUrl, String transcript) {
    }

    // the analysis before the text is put together: the preview step shows the transcript, the sender may correct it,
    // then compose() builds the alert text. hasVoice + transcript == null = the voice note could not be transcribed
    public record Analysis(String baseText, boolean hasVoice, String transcript, String imageLine, String evidence,
                           String attachmentUrl, List<Long> fileIds) {
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
        Analysis a = analyze(baseText, photos, audio, requestId);
        return new Result(compose(a, a.transcript()), a.attachmentUrl(), a.transcript());
    }

    public Analysis analyze(String baseText, List<MultipartFile> photos, MultipartFile audio, String requestId) {
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
        // an empty / silent recording is not a voice note (the web recorder still makes a valid WAV of it)
        if (hasAudio && "audio/wav".equals(audioType) && audioBytes.length < MIN_WAV_BYTES) throw new ApiException(NO_VOICE);

        // 2) analyze before storing: a silent recording is rejected without leaving files behind.
        // AI 2 runs in the background while Gemini works
        CompletableFuture<Optional<VisionResult>> detection = detect(images, requestId);

        String heard = null;
        if (hasAudio) {
            GeminiGatekeeper.Transcription t = gemini.transcribeVoice(audioBytes, audioType);
            log.info("Web report {} voice: {}", requestId, t.status() == GeminiGatekeeper.VoiceStatus.HEARD ? "'" + t.text() + "'" : t.status());
            // Gemini answered and heard nobody: ask again. Gemini down (FAILED): keep the report, the file is attached
            if (t.status() == GeminiGatekeeper.VoiceStatus.NO_SPEECH) throw new ApiException(NO_VOICE);
            heard = t.text();
        }
        String imageLine = null;
        if (!images.isEmpty()) {
            Optional<String> description = gemini.describeImages(images);
            log.info("Web report {} photos: {}", requestId, description.map(d -> "'" + d + "'").orElse("not described"));
            imageLine = description.map(d -> IMAGE_LABEL + d).orElse(IMAGE_FAILED);
        }

        String evidence = detection
                .completeOnTimeout(Optional.empty(), VISION_MAX_WAIT_SECONDS, TimeUnit.SECONDS)
                .join()
                .filter(VisionResult::hasFindings)
                .map(VisionResult::summaryAr)
                .orElse(null);

        // 3) store: the alert keeps its attachments even if every analysis failed
        List<Long> ids = new ArrayList<>();
        if (hasAudio) ids.add(mediaFileRepository.save(new MediaFile(audioBytes, audioType)).getId());
        for (ImageInput i : images) ids.add(mediaFileRepository.save(new MediaFile(i.bytes(), i.mimeType())).getId());
        String attachmentUrl = ids.isEmpty() ? null
                : PREFIX + ids.stream().map(String::valueOf).collect(Collectors.joining(","));

        return new Analysis(hasText(baseText) ? baseText.trim() : "", hasAudio, heard, imageLine, evidence,
                attachmentUrl, List.copyOf(ids));
    }

    // transcript = what Gemini heard, or the sender's correction of it (preview -> confirm)
    public String compose(Analysis a, String transcript) {
        StringBuilder text = new StringBuilder(a.baseText());
        if (a.hasVoice()) addLine(text, hasText(transcript) ? VOICE_LABEL + transcript.trim() : VOICE_FAILED);
        if (a.imageLine() != null) addLine(text, a.imageLine());

        // the detection line goes last and is never cut off (it carries "حريق"/"دخان" for the classifier, like Telegram)
        String evidence = a.evidence();
        int room = evidence == null ? MAX_TEXT_LENGTH : MAX_TEXT_LENGTH - evidence.length() - 1;
        String body = text.length() > room ? text.substring(0, room) : text.toString();
        return evidence == null ? body : body + "\n" + evidence;
    }

    // a preview that was never confirmed: its files go too
    public void deleteFiles(List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) return;
        try {
            mediaFileRepository.deleteAllById(fileIds);
        } catch (Exception e) {
            log.warn("Could not delete {} unused media file(s): {}", fileIds.size(), e.getMessage());
        }
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