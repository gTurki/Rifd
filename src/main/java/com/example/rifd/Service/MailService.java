package com.example.rifd.Service;

import com.example.rifd.Entity.Incident;
import com.example.rifd.Entity.Staff;
import com.example.rifd.Entity.Task;
import com.example.rifd.Enum.IncidentStatus;
import com.example.rifd.Enum.StaffRole;
import com.example.rifd.Events.IncidentResolvedEvent;
import com.example.rifd.Repository.IncidentRepository;
import com.example.rifd.Repository.StaffRepository;
import com.example.rifd.Repository.TaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import static org.springframework.web.util.HtmlUtils.htmlEscape;

// emails (decision 147): E1 welcome + sign-in details for a new staff account, E2 report to the supervisors
// when an incident is RESOLVED. Built inside the caller's data, sent after the commit on a background thread.
// Never throws to the caller. Arabic, right-to-left HTML; every value from users is HTML-escaped.
@Slf4j
@Service
public class MailService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);

    private final MailClient mailClient;
    private final IncidentRepository incidentRepository;
    private final TaskRepository taskRepository;
    private final StaffRepository staffRepository;
    private final TransactionTemplate tx;
    private final String platformUrl;

    public MailService(MailClient mailClient, IncidentRepository incidentRepository, TaskRepository taskRepository,
                       StaffRepository staffRepository, PlatformTransactionManager transactionManager,
                       @Value("${whatsapp.platform-url:}") String platformUrl) {
        this.mailClient = mailClient;
        this.incidentRepository = incidentRepository;
        this.taskRepository = taskRepository;
        this.staffRepository = staffRepository;
        this.tx = new TransactionTemplate(transactionManager);
        tx.setReadOnly(true);
        this.platformUrl = platformUrl == null ? "" : platformUrl.trim();
    }

    // ---------------- E1 ----------------

    public void staffCreated(Staff staff, String username, String password) {
        if (!mailClient.isEnabled() || !StringUtils.hasText(staff.getEmail())) return;
        try {
            String body = "<p>مرحبًا " + htmlEscape(staff.getName()) + "،</p>"
                    + "<p>تم إنشاء حسابك في مركز عمليات إكسبو 2030 (رِفد). بيانات الدخول:</p>"
                    + "<table style=\"border-collapse:collapse\">"
                    + row("اسم المستخدم", "<code dir=\"ltr\">" + htmlEscape(username) + "</code>")
                    + row("كلمة المرور المؤقتة", "<code dir=\"ltr\">" + htmlEscape(password) + "</code>")
                    + "</table>"
                    + (platformUrl.isEmpty() ? "" : "<p>رابط المنصة: <a href=\"" + htmlEscape(platformUrl) + "\">"
                    + htmlEscape(platformUrl) + "</a></p>")
                    + "<p><b>يرجى تغيير كلمة المرور بعد أول تسجيل دخول.</b></p>";
            String to = staff.getEmail();
            String name = staff.getName();
            afterCommit(() -> mailClient.send(to, name, "حسابك في رِفد — إكسبو 2030", page(body)));
        } catch (Exception e) {
            log.warn("Email E1 skipped for staff #{}: {}", staff.getId(), e.getClass().getSimpleName());
        }
    }

    // ---------------- E2 ----------------

    // after the closing is committed; a reopened incident (before this runs) sends nothing
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIncidentResolved(IncidentResolvedEvent event) {
        if (!mailClient.isEnabled() || event.finalStatus() != IncidentStatus.RESOLVED) return;
        try {
            Report report = tx.execute(s -> buildReport(event.incidentId()));
            if (report == null) return;
            Set<String> sent = new HashSet<>();
            for (Recipient r : report.recipients()) {
                if (sent.add(r.email().trim().toLowerCase(Locale.ROOT))) {
                    mailClient.send(r.email(), r.name(), report.subject(), report.html());
                }
            }
        } catch (Exception e) {
            log.warn("Email E2 skipped for incident #{}: {}", event.incidentId(), e.getClass().getSimpleName());
        }
    }

    private record Recipient(String email, String name) {
    }

    private record Report(String subject, String html, List<Recipient> recipients) {
    }

    private Report buildReport(Long incidentId) {
        Incident i = incidentRepository.findById(incidentId).orElse(null);
        if (i == null || i.getStatus() != IncidentStatus.RESOLVED) return null;

        List<Recipient> recipients = staffRepository.findByRole(StaffRole.SUPERVISOR).stream()
                .filter(s -> StringUtils.hasText(s.getEmail()))
                .map(s -> new Recipient(s.getEmail(), s.getName()))
                .toList();
        if (recipients.isEmpty()) return null;

        List<Task> tasks = taskRepository.findByIncidentIdOrderByAssignedAtAsc(incidentId);
        LocalDateTime firstOnSite = tasks.stream().map(Task::getArrivedAt).filter(Objects::nonNull)
                .min(LocalDateTime::compareTo).orElse(null);
        LocalDateTime firstAccepted = tasks.stream().map(Task::getAcceptedAt).filter(Objects::nonNull)
                .min(LocalDateTime::compareTo).orElse(null);
        String response = firstOnSite != null ? duration(i.getCreatedAt(), firstOnSite) + " (حتى وصول أول موظف)"
                : firstAccepted != null ? duration(i.getCreatedAt(), firstAccepted) + " (حتى قبول أول مهمة)"
                : "—";

        StringBuilder staff = new StringBuilder();
        for (Task t : tasks) {
            staff.append("<li>").append(htmlEscape(t.getStaff().getName()))
                    .append(" — ").append(htmlEscape(String.valueOf(t.getStatus()))).append("</li>");
        }

        String zone = i.getZone() == null ? "غير محدد" : htmlEscape(i.getZone().getName() + " (" + i.getZone().getCode() + ")");
        String body = "<p>أُغلقت الحادثة <b>" + htmlEscape(i.getCode()) + "</b>: " + htmlEscape(i.getTitle()) + "</p>"
                + "<table style=\"border-collapse:collapse\">"
                + row("الفئة", WhatsAppService.category(i.getCategory()))
                + row("الخطورة عند البلاغ", WhatsAppService.severity(i.getSeverity()))
                + row("الخطورة الفعلية", WhatsAppService.severity(i.getActualSeverity() != null ? i.getActualSeverity() : i.getSeverity()))
                + row("المكان", zone)
                + row("عدد البلاغات", String.valueOf(i.getAlertCount() == null ? 0 : i.getAlertCount()))
                + row("وقت الإنشاء", time(i.getCreatedAt()))
                + row("وقت الاطلاع", time(i.getAcknowledgedAt()))
                + row("وقت الإغلاق", time(i.getResolvedAt()))
                + row("زمن الاستجابة", response)
                + row("المدة الكلية", duration(i.getCreatedAt(), i.getResolvedAt()))
                + "</table>"
                + "<p><b>الموظفون:</b></p>" + (staff.isEmpty() ? "<p>لم يُسند أحد</p>" : "<ul>" + staff + "</ul>")
                + "<p><b>الملاحظات:</b> " + (StringUtils.hasText(i.getResolutionNotes()) ? htmlEscape(i.getResolutionNotes()) : "—") + "</p>";
        return new Report("تقرير إغلاق " + i.getCode() + " — رِفد", page(body), recipients);
    }

    // ---------------- helpers ----------------

    private static void afterCommit(Runnable send) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        send.run();
                    } catch (Exception e) {
                        log.warn("Email could not be queued: {}", e.getClass().getSimpleName());
                    }
                }
            });
        } else {
            send.run();
        }
    }

    private static String page(String body) {
        return "<div dir=\"rtl\" style=\"font-family:Tahoma,Arial,sans-serif;font-size:14px;line-height:1.7\">"
                + body + "<p style=\"color:#888;font-size:12px\">رِفد · مركز عمليات إكسبو 2030 الرياض</p></div>";
    }

    // value is already safe HTML
    private static String row(String label, String value) {
        return "<tr><td style=\"padding:4px 12px;color:#555\">" + label + "</td><td style=\"padding:4px 12px\">"
                + value + "</td></tr>";
    }

    private static String time(LocalDateTime t) {
        return t == null ? "—" : t.format(TIME);
    }

    private static String duration(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null || to.isBefore(from)) return "—";
        long s = Duration.between(from, to).getSeconds();
        return s >= 3600 ? (s / 3600) + " س " + (s % 3600 / 60) + " د"
                : (s / 60) + " د " + (s % 60) + " ث";
    }
}
