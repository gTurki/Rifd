package com.example.rifd.Telegram;

import com.example.rifd.Api.ApiException;
import com.example.rifd.DTOOut.TelegramReporterDTOOut;
import com.example.rifd.Entity.TelegramReporter;
import com.example.rifd.Enum.AlertStatus;
import com.example.rifd.Enum.IncidentStatus;
import com.example.rifd.Enum.ReporterTrust;
import com.example.rifd.Repository.TelegramReporterRepository;
import com.example.rifd.Service.IncidentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramReporterService {

    // unforgiven false reports that block a reporter automatically (checked at their next message)
    public static final int AUTO_BLOCK_FALSE_REPORTS = 3;

    public record Standing(long reports, long confirmed, long falseReports, ReporterTrust trust) {
    }

    private final TelegramReporterRepository repository;
    private final IncidentService incidentService;

    private final Object registerLock = new Object();

    // ---------------- bot side ----------------

    // one row per Telegram user, refreshed on every message
    public TelegramReporter register(long telegramUserId, String firstName, String username) {
        String name = cut(firstName);
        String user = cut(username);
        synchronized (registerLock) {
            if (repository.touch(telegramUserId, name, user, LocalDateTime.now()) == 0) {
                TelegramReporter reporter = new TelegramReporter();
                reporter.setTelegramUserId(telegramUserId);
                reporter.setFirstName(name);
                reporter.setUsername(user);
                reporter.setLastMessageAt(LocalDateTime.now());
                try {
                    repository.save(reporter);
                } catch (DataIntegrityViolationException e) {
                    // created at the same moment by another path: fine, read it below
                }
            }
        }
        return repository.findByTelegramUserId(telegramUserId)
                .orElseThrow(() -> new IllegalStateException("Telegram reporter was not saved"));
    }

    // false = blocked (manually, or automatically right now)
    @Transactional
    public boolean mayReport(Long reporterId) {
        TelegramReporter reporter = repository.findById(reporterId)
                .orElseThrow(() -> new ApiException("Reporter not found"));
        if (Boolean.TRUE.equals(reporter.getBlocked())) return false;

        long falseReports = countFalse(reporterId);
        if (falseReports - reporter.getForgivenFalseReports() >= AUTO_BLOCK_FALSE_REPORTS) {
            reporter.setBlocked(true);
            reporter.setBlockedAt(LocalDateTime.now());
            reporter.setBlockedBy(null);
            reporter.setBlockReason("AUTO: " + falseReports + " false reports");
            repository.save(reporter);
            log.warn("Telegram reporter #{} (tg {}) auto-blocked: {} false reports",
                    reporterId, reporter.getTelegramUserId(), falseReports);
            return false;
        }
        return true;
    }

    @Transactional(readOnly = true)
    public Standing standing(Long reporterId) {
        TelegramReporter reporter = repository.findById(reporterId)
                .orElseThrow(() -> new ApiException("Reporter not found"));
        return standing(reporter);
    }

    // Saudi mobile only. empty = not a Saudi mobile number
    @Transactional
    public Optional<String> verifyPhone(long telegramUserId, String rawPhone) {
        String phone = normalizeSaudiMobile(rawPhone);
        if (phone == null) return Optional.empty();

        TelegramReporter reporter = repository.findByTelegramUserId(telegramUserId)
                .orElseThrow(() -> new ApiException("Reporter not found"));
        reporter.setPhone(phone);
        reporter.setPhoneVerifiedAt(LocalDateTime.now());
        repository.save(reporter);
        log.info("Telegram reporter #{} verified phone {}", reporter.getId(), mask(phone));
        return Optional.of(phone);
    }

    // ---------------- dashboard side ----------------

    @Transactional(readOnly = true)
    public List<TelegramReporterDTOOut> search(Boolean blocked) {
        List<TelegramReporter> reporters = blocked == null
                ? repository.findAllByOrderByLastMessageAtDesc()
                : repository.findByBlockedOrderByLastMessageAtDesc(blocked);
        return reporters.stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public TelegramReporterDTOOut getById(Long id) {
        return toDTO(find(id));
    }

    @Transactional
    public TelegramReporterDTOOut block(Long id, String reason, Long actorStaffId) {
        incidentService.validateOperator(actorStaffId);
        TelegramReporter reporter = find(id);

        reporter.setBlocked(true);
        reporter.setBlockedAt(LocalDateTime.now());
        reporter.setBlockedBy(actorStaffId);
        reporter.setBlockReason(reason.trim());
        repository.save(reporter);

        log.info("Telegram reporter #{} blocked by staff {}: {}", id, actorStaffId, reason.trim());
        return toDTO(reporter);
    }

    // a fresh start: false reports so far are forgiven, only new ones count toward the next auto-block
    @Transactional
    public TelegramReporterDTOOut unblock(Long id, String reason, Long actorStaffId) {
        incidentService.validateOperator(actorStaffId);
        TelegramReporter reporter = find(id);
        if (!Boolean.TRUE.equals(reporter.getBlocked())) return toDTO(reporter);

        reporter.setBlocked(false);
        reporter.setBlockedAt(null);
        reporter.setBlockedBy(null);
        reporter.setBlockReason(null);
        reporter.setForgivenFalseReports((int) countFalse(id));
        repository.save(reporter);

        log.info("Telegram reporter #{} unblocked by staff {}: {}", id, actorStaffId, reason.trim());
        return toDTO(reporter);
    }

    // ---------------- helpers ----------------

    private TelegramReporter find(Long id) {
        return repository.findById(id).orElseThrow(() -> new ApiException("Reporter not found"));
    }

    private Standing standing(TelegramReporter reporter) {
        long reports = repository.countReports(reporter.getId());
        long confirmed = repository.countConfirmedReports(reporter.getId(), AlertStatus.DISMISSED, IncidentStatus.RESOLVED);
        long falseReports = countFalse(reporter.getId());
        long unforgiven = falseReports - reporter.getForgivenFalseReports();

        // a warning beats everything else
        ReporterTrust trust;
        if (unforgiven > 0) trust = ReporterTrust.FLAGGED;
        else if (reporter.getPhoneVerifiedAt() != null) trust = ReporterTrust.VERIFIED;
        else if (confirmed > 0) trust = ReporterTrust.TRUSTED;
        else trust = ReporterTrust.NEW;

        return new Standing(reports, confirmed, falseReports, trust);
    }

    private long countFalse(Long reporterId) {
        return repository.countFalseReports(reporterId, AlertStatus.DISMISSED, IncidentStatus.FALSE_ALARM);
    }

    private TelegramReporterDTOOut toDTO(TelegramReporter r) {
        Standing s = standing(r);
        return new TelegramReporterDTOOut(r.getId(), r.getTelegramUserId(), r.getFirstName(), r.getUsername(),
                mask(r.getPhone()), r.getPhoneVerifiedAt() != null, s.trust(),
                s.reports(), s.confirmed(), s.falseReports(),
                r.getBlocked(), r.getBlockedAt(), r.getBlockReason(), r.getBlockedBy(),
                r.getLastMessageAt(), r.getCreatedAt());
    }

    // +966501234567 / 966501234567 / 00966501234567 / 0501234567 -> +966501234567, anything else -> null
    static String normalizeSaudiMobile(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("\\D", "");
        if (digits.startsWith("00")) digits = digits.substring(2);
        if (digits.length() == 10 && digits.startsWith("05")) digits = "966" + digits.substring(1);
        return digits.matches("9665\\d{8}") ? "+" + digits : null;
    }

    // +966501234567 -> +9665*****567 (the full number never leaves the server)
    private static String mask(String phone) {
        if (phone == null || phone.length() < 9) return null;
        return phone.substring(0, 5) + "*****" + phone.substring(phone.length() - 3);
    }

    private static String cut(String value) {
        if (value == null) return null;
        return value.length() <= 255 ? value : value.substring(0, 255);
    }
}