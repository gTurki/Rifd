package com.example.rifd.Service;

import com.example.rifd.Entity.Incident;
import com.example.rifd.Entity.Staff;
import com.example.rifd.Enum.IncidentCategory;
import com.example.rifd.Enum.Severity;
import com.example.rifd.Enum.StaffRole;
import com.example.rifd.Repository.StaffRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// WhatsApp messages to staff (decision 147): W1 new account, W2 HIGH task assigned, W3 escalation.
// The Arabic text is built now (inside the caller's transaction, lazy fields are readable),
// the send happens only after the commit and on a background thread. Never throws to the caller.
@Slf4j
@Service
public class WhatsAppService {

    private final WhatsAppClient whatsAppClient;
    private final StaffRepository staffRepository;
    private final String platformUrl;
    // W2: "incidentId|phone" already messaged (in memory: a restart forgets it, incident ids are never reused)
    private final Set<String> sentKeys = ConcurrentHashMap.newKeySet();

    public WhatsAppService(WhatsAppClient whatsAppClient, StaffRepository staffRepository,
                           @Value("${whatsapp.platform-url:}") String platformUrl) {
        this.whatsAppClient = whatsAppClient;
        this.staffRepository = staffRepository;
        this.platformUrl = platformUrl == null ? "" : platformUrl.trim();
    }

    // W1: new account -> username + temporary password + link
    public void staffCreated(Staff staff, String username, String password) {
        if (!whatsAppClient.isEnabled()) return;
        try {
            String body = "مرحبًا " + staff.getName() + "،\n"
                    + "تم إنشاء حسابك في مركز عمليات إكسبو 2030 (رِفد).\n"
                    + "اسم المستخدم: " + username + "\n"
                    + "كلمة المرور المؤقتة: " + password + "\n"
                    + link("رابط المنصة: ")
                    + "يرجى تغيير كلمة المرور بعد أول تسجيل دخول.";
            sendAfterCommit(staff.getPhone(), body, null);
        } catch (Exception e) {
            log.warn("WhatsApp W1 skipped for staff #{}: {}", staff.getId(), e.getClass().getSimpleName());
        }
    }

    // W2: task assigned on a HIGH incident -> the field staff member (once per phone per incident)
    public void taskAssigned(Staff staff, Incident incident) {
        if (!whatsAppClient.isEnabled() || incident.getSeverity() != Severity.HIGH) return;
        try {
            String body = "🚨 مهمة جديدة " + incident.getCode() + "\n"
                    + "النوع: " + category(incident.getCategory()) + "\n"
                    + "الخطورة: " + severity(incident.getSeverity()) + "\n"
                    + "المكان: " + location(incident) + "\n"
                    + link("افتح التطبيق لقبول المهمة: ");
            String to = WhatsAppClient.normalize(staff.getPhone());
            if (to != null) sendAfterCommit(to, body.trim(), incident.getId() + "|" + to);
        } catch (Exception e) {
            log.warn("WhatsApp W2 skipped for incident #{}: {}", incident.getId(), e.getClass().getSimpleName());
        }
    }

    // W3: the automatic re-assignment found nobody -> every supervisor with a phone (once per number)
    public void escalated(Incident incident) {
        if (!whatsAppClient.isEnabled()) return;
        try {
            String body = "⚠️ تصعيد: الحادثة " + incident.getCode() + "\n"
                    + "النوع: " + category(incident.getCategory())
                    + " — الخطورة: " + severity(incident.getSeverity()) + "\n"
                    + "المكان: " + location(incident) + "\n"
                    + "لم يُعثر على موظف بديل تلقائيًا، تحتاج إسناد يدوي.\n"
                    + link("");
            Set<String> sent = new HashSet<>();
            for (Staff supervisor : staffRepository.findByRole(StaffRole.SUPERVISOR)) {
                String to = WhatsAppClient.normalize(supervisor.getPhone());
                if (to != null && sent.add(to)) sendAfterCommit(to, body.trim(), null);
            }
        } catch (Exception e) {
            log.warn("WhatsApp W3 skipped for incident #{}: {}", incident.getId(), e.getClass().getSimpleName());
        }
    }

    // same idea as BroadcastService: nothing is sent if the transaction rolls back.
    // onceKey != null -> sent at most once per key (checked at commit time, so a rolled-back
    // assignment does not use up the key)
    private void sendAfterCommit(String phone, String body, String onceKey) {
        if (!StringUtils.hasText(phone)) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendOnce(phone, body, onceKey);
                }
            });
        } else {
            sendOnce(phone, body, onceKey);
        }
    }

    // runs inside afterCommit: an exception here would reach the caller although the data is committed
    private void sendOnce(String phone, String body, String onceKey) {
        try {
            if (onceKey == null || sentKeys.add(onceKey)) whatsAppClient.send(phone, body);
        } catch (Exception e) {
            log.warn("WhatsApp could not be queued: {}", e.getClass().getSimpleName());
        }
    }

    private String link(String label) {
        return platformUrl.isEmpty() ? "" : label + platformUrl + "\n";
    }

    private static String location(Incident incident) {
        if (incident.getZone() == null) return "غير محدد";
        return incident.getZone().getName() + " (" + incident.getZone().getCode() + ")";
    }

    // shared with MailService (Arabic labels)
    static String severity(Severity severity) {
        if (severity == null) return "غير محددة";
        return switch (severity) {
            case HIGH -> "عالية";
            case MEDIUM -> "متوسطة";
            case LOW -> "منخفضة";
        };
    }

    static String category(IncidentCategory category) {
        if (category == null) return "أخرى";
        return switch (category) {
            case CROWD -> "ازدحام";
            case MEDICAL -> "حالة طبية";
            case FIRE -> "حريق";
            case SECURITY -> "أمني";
            case TECHNICAL_FAILURE -> "عطل فني";
            case ACCESSIBILITY -> "إتاحة";
            case LOST_PERSON -> "شخص مفقود";
            case CLEANLINESS -> "نظافة";
            case OTHER -> "أخرى";
        };
    }
}
