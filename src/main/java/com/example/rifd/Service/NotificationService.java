package com.example.rifd.Service;

import com.example.rifd.Api.ApiException;
import com.example.rifd.DTOOut.NotificationDTOOut;
import com.example.rifd.Entity.Notification;
import com.example.rifd.Entity.Staff;
import com.example.rifd.Enum.NotificationType;
import com.example.rifd.Enum.Severity;
import com.example.rifd.Enum.StaffRole;
import com.example.rifd.Enum.StaffSpecialty;
import com.example.rifd.Mapper.DTOMapper;
import com.example.rifd.Repository.IncidentRepository;
import com.example.rifd.Repository.NotificationRepository;
import com.example.rifd.Repository.StaffRepository;
import com.example.rifd.Security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final StaffRepository staffRepository;
    private final IncidentRepository incidentRepository;
    private final BroadcastService broadcastService;
    private final CurrentUser currentUser;

    @Transactional
    public void notifyStaff(Long staffId, Long incidentId, NotificationType type, String message, Severity priority) {
        Notification n = build(incidentId, type, message, priority);
        n.setRecipientStaff(staffRepository.getReferenceById(staffId));
        save(n);
    }

    @Transactional
    public void notifySpecialty(StaffSpecialty specialty, Long incidentId, NotificationType type,
                                String message, Severity priority) {
        Notification n = build(incidentId, type, message, priority);
        n.setRecipientSpecialty(specialty);
        save(n);
    }

    // one notification per supervisor (escalations)
    @Transactional
    public void notifySupervisors(Long incidentId, String message, Severity priority) {
        for (Staff supervisor : staffRepository.findByRole(StaffRole.SUPERVISOR)) {
            notifyStaff(supervisor.getId(), incidentId, NotificationType.ESCALATION, message, priority);
        }
    }

    // staffId -> the person's own notifications + those sent to their specialty
    // specialty only -> that specialty; nothing -> everything (operator view)
    @Transactional(readOnly = true)
    public List<NotificationDTOOut> search(Long staffId, StaffSpecialty specialty, boolean unreadOnly) {
        List<Notification> result;
        if (staffId != null) {
            Staff staff = staffRepository.findById(staffId).orElseThrow(() -> new ApiException("Staff not found"));
            StaffSpecialty sp = specialty != null ? specialty : staff.getSpecialty();
            result = notificationRepository.findForRecipient(staffId, sp, unreadOnly);
        } else if (specialty != null) {
            result = notificationRepository.findForRecipient(null, specialty, unreadOnly);
        } else {
            result = unreadOnly
                    ? notificationRepository.findByReadAtIsNullOrderBySentAtDesc()
                    : notificationRepository.findAllByOrderBySentAtDesc();
        }
        return result.stream().map(DTOMapper::toDTO).toList();
    }

    @Transactional
    public NotificationDTOOut markRead(Long notificationId) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ApiException("Notification not found"));
        if (n.getRecipientStaff() != null) {
            currentUser.requireSelfIfField(n.getRecipientStaff().getId());
        } else if (n.getRecipientSpecialty() != null && currentUser.isField()) {
            // a field user may only mark their own team's notifications as read
            StaffSpecialty mine = currentUser.staffId().flatMap(staffRepository::findById)
                    .map(Staff::getSpecialty).orElse(null);
            if (mine != n.getRecipientSpecialty()) {
                throw new ApiException("You can only act on your own team's notifications");
            }
        }
        if (n.getReadAt() == null) { // idempotent
            n.setReadAt(LocalDateTime.now());
            notificationRepository.save(n);
        }
        return DTOMapper.toDTO(n);
    }

    private Notification build(Long incidentId, NotificationType type, String message, Severity priority) {
        Notification n = new Notification();
        n.setIncident(incidentId == null ? null : incidentRepository.getReferenceById(incidentId));
        n.setType(type);
        n.setMessage(message);
        n.setPriority(priority);
        n.setSentAt(LocalDateTime.now());
        return n;
    }

    private void save(Notification n) {
        notificationRepository.save(n);
        broadcastService.send(BroadcastService.NOTIFICATIONS, "NOTIFICATION_CREATED", DTOMapper.toDTO(n));
    }
}
