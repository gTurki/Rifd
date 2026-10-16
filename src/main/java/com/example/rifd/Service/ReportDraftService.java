package com.example.rifd.Service;

import com.example.rifd.Api.ApiException;
import com.example.rifd.DTOIn.ReportConfirmDTOIn;
import com.example.rifd.DTOOut.ReportPreviewDTOOut;
import com.example.rifd.Entity.Zone;
import com.example.rifd.Repository.ZoneRepository;
import com.example.rifd.Telegram.GeminiGatekeeper;
import com.example.rifd.Telegram.GeminiGatekeeper.ZoneOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.util.StringUtils.hasText;

// web reports in two steps (visitor page + staff app): preview = analyze the photos / voice note and guess the zone,
// nothing reaches the pipeline yet; confirm = the sender checked (and maybe corrected) what we heard and the place.
// Drafts live in memory for a few minutes (a restart forgets them: the sender just sends again);
// an expired draft's files are deleted, so abandoned previews leave nothing behind.
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportDraftService {

    public enum Kind { VISITOR, STAFF }

    public record Draft(String id, Kind kind, MediaReportService.Analysis analysis, String language,
                        Double x, Double y, String sourceReference, Instant createdAt) {
    }

    // the report as it goes to the pipeline
    public record Confirmed(Draft draft, String text, String transcript, String zoneCode) {
    }

    private static final Duration TTL = Duration.ofMinutes(15);
    private static final int MAX_DRAFTS = 300;
    private static final String EXPIRED = "انتهت مهلة المراجعة، أرسل البلاغ مرة ثانية";

    private final Map<String, Draft> drafts = new ConcurrentHashMap<>();

    private final MediaReportService mediaReportService;
    private final GeminiGatekeeper gemini;
    private final ZoneRepository zoneRepository;

    public Draft create(Kind kind, String text, String language, Double x, Double y, String zoneCode,
                                           String sourceReference, List<MultipartFile> photos, MultipartFile audio) {
        String id = UUID.randomUUID().toString();
        MediaReportService.Analysis analysis = mediaReportService.analyze(text, photos, audio, "draft-" + id);
        makeRoom();
        Draft draft = new Draft(id, kind, analysis, hasText(language) ? language.trim() : null, x, y,
                hasText(sourceReference) ? sourceReference.trim() : null, Instant.now());
        drafts.put(id, draft);
        return draft;
    }

    // zone: the one the sender picked, else the one Gemini reads in the text (never guessed: null = ask the sender)
    public ReportPreviewDTOOut preview(Draft draft, String pickedZoneCode) {
        MediaReportService.Analysis a = draft.analysis();
        Optional<Zone> zone = hasText(pickedZoneCode)
                ? zoneRepository.findByCode(pickedZoneCode.trim().toUpperCase(Locale.ROOT))
                : Optional.empty();
        boolean guessed = false;
        if (zone.isEmpty() && (draft.x() == null || draft.y() == null)) {
            List<Zone> all = zoneRepository.findAll();
            List<ZoneOption> options = all.stream().map(z -> new ZoneOption(z.getCode(), z.getName())).toList();
            String code = gemini.check(mediaReportService.compose(a, a.transcript()), options)
                    .map(GeminiGatekeeper.Verdict::zoneCode).orElse(null);
            zone = all.stream().filter(z -> z.getCode().equals(code)).findFirst();
            guessed = zone.isPresent();
        }
        boolean hasPosition = draft.x() != null && draft.y() != null;
        return new ReportPreviewDTOOut(draft.id(), a.hasVoice(), a.transcript(),
                zone.map(Zone::getCode).orElse(null), zone.map(Zone::getName).orElse(null),
                guessed, zone.isEmpty() && !hasPosition);
    }

    // the draft is used once: taken out here, put back only if the pipeline fails (the sender can retry)
    public Confirmed confirm(Kind kind, ReportConfirmDTOIn dto) {
        Draft draft = drafts.remove(dto.draftId().trim());
        if (draft == null || draft.kind() != kind || expired(draft)) {
            if (draft != null) mediaReportService.deleteFiles(draft.analysis().fileIds());
            throw new ApiException(EXPIRED);
        }
        MediaReportService.Analysis a = draft.analysis();
        // the sender may correct (or, if it failed, type) what was said; blank keeps what we heard
        String transcript = a.hasVoice() && hasText(dto.transcript()) ? dto.transcript().trim() : a.transcript();
        String zoneCode = hasText(dto.zoneCode()) ? dto.zoneCode().trim().toUpperCase(Locale.ROOT) : null;
        return new Confirmed(draft, mediaReportService.compose(a, transcript), transcript, zoneCode);
    }

    public void restore(Draft draft) {
        if (!expired(draft)) drafts.put(draft.id(), draft);
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void expireOld() {
        drafts.values().stream().filter(ReportDraftService::expired).toList().forEach(this::drop);
    }

    private void makeRoom() {
        expireOld();
        while (drafts.size() >= MAX_DRAFTS) {
            drafts.values().stream().min(Comparator.comparing(Draft::createdAt)).ifPresent(this::drop);
        }
    }

    private void drop(Draft draft) {
        if (drafts.remove(draft.id(), draft)) mediaReportService.deleteFiles(draft.analysis().fileIds());
    }

    private static boolean expired(Draft draft) {
        return draft.createdAt().plus(TTL).isBefore(Instant.now());
    }
}
