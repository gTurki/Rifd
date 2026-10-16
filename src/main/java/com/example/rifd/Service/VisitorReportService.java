package com.example.rifd.Service;

import com.example.rifd.Api.ApiException;
import com.example.rifd.DTOIn.AlertDTOIn;
import com.example.rifd.DTOIn.ReportConfirmDTOIn;
import com.example.rifd.DTOIn.VisitorReportDTOIn;
import com.example.rifd.DTOOut.ReportPreviewDTOOut;
import com.example.rifd.DTOOut.VisitorReportDTOOut;
import com.example.rifd.Entity.Alert;
import com.example.rifd.Entity.Incident;
import com.example.rifd.Enum.AlertSource;
import com.example.rifd.Enum.AlertStatus;
import com.example.rifd.Enum.IncidentStatus;
import com.example.rifd.Repository.AlertRepository;
import com.example.rifd.Security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.List;


// visitor app: a normal alert (VISITOR_APP, same pipeline) + a random tracking code kept in sourceReference
// ("rf:RF-XXXXXX", like Telegram's "tg:..."). Random on purpose: sequential ids would let anyone read other reports
@Service
@RequiredArgsConstructor
public class VisitorReportService {

    public static final String REF_PREFIX = "rf:";
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final String DRAFT_OWNER = "visitor:";
    public static final String SIGN_IN_FIRST = "سجّل دخولك أولًا عشان ترسل بلاغ";

    private final SecureRandom random = new SecureRandom();
    private final AlertService alertService;
    private final AlertRepository alertRepository;
    private final MediaReportService mediaReportService;
    private final ReportDraftService reportDraftService;
    private final CurrentUser currentUser;

    // every report needs a signed-in visitor (visitor page sign-in); the report is linked to the account
    public VisitorReportDTOOut submit(VisitorReportDTOIn dto) {
        Long visitorId = requireVisitor();
        String code = newCode();
        create(code, dto.text().trim(), dto.language(), dto.x(), dto.y(), dto.zoneCode(), null, visitorId);
        return track(code);
    }

    // same report with photos / a voice note (multipart). The analysis is added to the text before the pipeline
    public VisitorReportDTOOut submitMedia(String text, String language, Double x, Double y, String zoneCode,
                                           List<MultipartFile> photos, MultipartFile audio) {
        Long visitorId = requireVisitor();
        String code = newCode();
        MediaReportService.Result media = mediaReportService.process(text, photos, audio, "web-" + code);
        create(code, media.text(), language, x, y, zoneCode, media.attachmentUrl(), visitorId);
        return toDTO(code, find(code), media.transcript()); // the sender sees what we heard
    }

    // two steps: preview (what we heard + where) -> the visitor checks / corrects it -> confirm.
    // the draft remembers its visitor: only they can confirm it
    public ReportPreviewDTOOut preview(String text, String language, Double x, Double y, String zoneCode,
                                       List<MultipartFile> photos, MultipartFile audio) {
        Long visitorId = requireVisitor();
        ReportDraftService.Draft draft = reportDraftService.create(ReportDraftService.Kind.VISITOR, text, language,
                x, y, zoneCode, DRAFT_OWNER + visitorId, photos, audio);
        return reportDraftService.preview(draft, zoneCode);
    }

    public VisitorReportDTOOut confirm(ReportConfirmDTOIn dto) {
        Long visitorId = requireVisitor();
        ReportDraftService.Confirmed c = reportDraftService.confirm(ReportDraftService.Kind.VISITOR, dto);
        ReportDraftService.Draft d = c.draft();
        if (!(DRAFT_OWNER + visitorId).equals(d.sourceReference())) {
            reportDraftService.restore(d);
            throw new ApiException("Report not found");
        }
        String code = newCode();
        try {
            create(code, c.text(), d.language(), d.x(), d.y(), c.zoneCode(), d.analysis().attachmentUrl(), visitorId);
        } catch (RuntimeException e) {
            reportDraftService.restore(d); // the visitor can press send again
            throw e;
        }
        return toDTO(code, find(code), c.transcript());
    }

    // "my reports": the signed-in visitor's reports, newest first
    public List<VisitorReportDTOOut> mine() {
        Long visitorId = requireVisitor();
        return alertRepository.findTop50ByVisitorIdOrderByReceivedAtDesc(visitorId).stream()
                .filter(a -> a.getSourceReference() != null && a.getSourceReference().startsWith(REF_PREFIX))
                .map(a -> toDTO(a.getSourceReference().substring(REF_PREFIX.length()), a, null))
                .toList();
    }

    private Long requireVisitor() {
        return currentUser.visitorId().orElseThrow(() -> new ApiException(SIGN_IN_FIRST));
    }

    private void create(String code, String text, String language, Double x, Double y, String zoneCode,
                        String attachmentUrl, Long visitorId) {
        AlertDTOIn alert = new AlertDTOIn(AlertSource.VISITOR_APP, REF_PREFIX + code, text,
                language, attachmentUrl, x, y,
                StringUtils.hasText(zoneCode) ? zoneCode.trim() : null, null);
        alertService.ingestForVisitor(alert, visitorId); // runs the whole pipeline (classification + merge)
    }

    public VisitorReportDTOOut track(String rawCode) {
        String code = normalize(rawCode);
        return toDTO(code, find(code), null);
    }

    private Alert find(String code) {
        return alertRepository.findFirstBySourceReference(REF_PREFIX + code)
                .orElseThrow(() -> new ApiException("Report not found"));
    }

    // incident + zone are fetched with the alert (@EntityGraph): no session needed here
    private static VisitorReportDTOOut toDTO(String code, Alert alert, String transcript) {
        Incident incident = alert.getIncident();
        IncidentStatus status = incident == null ? null : incident.getStatus();

        int step;
        String state;
        if (alert.getStatus() == AlertStatus.DISMISSED || status == IncidentStatus.FALSE_ALARM) {
            step = 3; state = "CLOSED";
        } else if (status == IncidentStatus.RESOLVED) {
            step = 3; state = "RESOLVED";
        } else if (status == IncidentStatus.IN_PROGRESS) {
            step = 2; state = "IN_PROGRESS";
        } else if (status == IncidentStatus.ASSIGNED) {
            step = 1; state = "ASSIGNED";
        } else {
            step = 0; state = "RECEIVED";
        }
        return new VisitorReportDTOOut(code, step, state,
                alert.getZone() == null ? null : alert.getZone().getCode(),
                alert.getReceivedAt(),
                incident == null ? alert.getUpdatedAt() : incident.getUpdatedAt(),
                transcript);
    }

    private String newCode() {
        for (int i = 0; i < 20; i++) {
            StringBuilder sb = new StringBuilder("RF-");
            for (int j = 0; j < 6; j++) sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
            String code = sb.toString();
            if (alertRepository.findFirstBySourceReference(REF_PREFIX + code).isEmpty()) return code;
        }
        throw new ApiException("Could not create a tracking code, try again");
    }

    // accepts "rf-ab12cd", "AB12CD", " RF-AB12CD "
    private static String normalize(String raw) {
        String c = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (!c.startsWith("RF-")) c = "RF-" + c;
        if (!c.matches("RF-[A-Z0-9]{6}")) throw new ApiException("Invalid tracking code");
        return c;
    }
}