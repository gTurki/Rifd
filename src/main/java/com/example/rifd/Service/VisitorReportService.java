package com.example.rifd.Service;

import com.example.rifd.Api.ApiException;
import com.example.rifd.DTOIn.AlertDTOIn;
import com.example.rifd.DTOIn.VisitorReportDTOIn;
import com.example.rifd.DTOOut.VisitorReportDTOOut;
import com.example.rifd.Entity.Alert;
import com.example.rifd.Entity.Incident;
import com.example.rifd.Enum.AlertSource;
import com.example.rifd.Enum.AlertStatus;
import com.example.rifd.Enum.IncidentStatus;
import com.example.rifd.Repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.Locale;

// visitor app: a normal alert (VISITOR_APP, same pipeline) + a random tracking code kept in sourceReference
// ("rf:RF-XXXXXX", like Telegram's "tg:..."). Random on purpose: sequential ids would let anyone read other reports
@Service
@RequiredArgsConstructor
public class VisitorReportService {

    public static final String REF_PREFIX = "rf:";
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final SecureRandom random = new SecureRandom();
    private final AlertService alertService;
    private final AlertRepository alertRepository;

    public VisitorReportDTOOut submit(VisitorReportDTOIn dto) {
        String code = newCode();
        AlertDTOIn alert = new AlertDTOIn(AlertSource.VISITOR_APP, REF_PREFIX + code, dto.text().trim(),
                dto.language(), null, dto.x(), dto.y(),
                StringUtils.hasText(dto.zoneCode()) ? dto.zoneCode().trim() : null, null);
        alertService.ingest(alert); // runs the whole pipeline (classification + merge) before returning
        return track(code);
    }

    public VisitorReportDTOOut track(String rawCode) {
        String code = normalize(rawCode);
        Alert alert = alertRepository.findFirstBySourceReference(REF_PREFIX + code)
                .orElseThrow(() -> new ApiException("Report not found"));
        return toDTO(code, alert);
    }

    // incident + zone are fetched with the alert (@EntityGraph): no session needed here
    private static VisitorReportDTOOut toDTO(String code, Alert alert) {
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
                incident == null ? alert.getUpdatedAt() : incident.getUpdatedAt());
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