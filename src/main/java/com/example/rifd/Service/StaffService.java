package com.example.rifd.Service;

import com.example.rifd.Api.ApiException;
import com.example.rifd.DTOOut.StaffDTOOut;
import com.example.rifd.Entity.Staff;
import com.example.rifd.Enum.StaffRole;
import com.example.rifd.Enum.StaffSpecialty;
import com.example.rifd.Enum.StaffStatus;
import com.example.rifd.Enum.TaskStatus;
import com.example.rifd.Mapper.DTOMapper;
import com.example.rifd.Repository.StaffRepository;
import com.example.rifd.Repository.TaskRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StaffService {

    // a task in one of these states keeps its staff member BUSY
    public static final Set<TaskStatus> ACTIVE_TASK = EnumSet.of(
            TaskStatus.PENDING, TaskStatus.ACCEPTED, TaskStatus.EN_ROUTE, TaskStatus.ON_SITE);

    private final StaffRepository staffRepository;
    private final TaskRepository taskRepository;
    private final BroadcastService broadcastService;
    private final EntityManager entityManager;

    // ---------------- read ----------------

    @Transactional(readOnly = true)
    public Staff getStaff(Long staffId) {
        return staffRepository.findById(staffId).orElseThrow(() -> new ApiException("Staff not found"));
    }

    @Transactional(readOnly = true)
    public List<StaffDTOOut> search(StaffStatus status, StaffSpecialty specialty, StaffRole role) {
        return staffRepository.search(status, specialty, role).stream().map(DTOMapper::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public StaffDTOOut getById(Long staffId) {
        return DTOMapper.toDTO(getStaff(staffId));
    }

    // ---------------- updates ----------------

    @Transactional
    public StaffDTOOut updateLocation(Long staffId, double x, double y) {
        Staff staff = getStaff(staffId);
        moveTo(staff, x, y);
        return DTOMapper.toDTO(staff);
    }

    // used by the API and by StaffMovementScheduler
    @Transactional
    public void moveTo(Staff staff, double x, double y) {
        staff.setCurrentX(x);
        staff.setCurrentY(y);
        staff.setLastLocationUpdate(LocalDateTime.now());
        staffRepository.save(staff);
        broadcast(staff);
    }

    @Transactional
    public StaffDTOOut updateStatus(Long staffId, StaffStatus newStatus) {
        Staff staff = getStaff(staffId);
        if (staff.getStatus() == newStatus) return DTOMapper.toDTO(staff);
        if (newStatus == StaffStatus.BUSY) throw new ApiException("BUSY is set by tasks only");

        // BUSY is owned by the task cycle: no AVAILABLE / OFF_DUTY while a task is still open
        if (newStatus != StaffStatus.BUSY && taskRepository.existsByStaffIdAndStatusIn(staffId, ACTIVE_TASK)) {
            throw new ApiException("Staff has an active task; finish or reject it first");
        }
        staff.setStatus(newStatus);
        staffRepository.save(staff);
        broadcast(staff);
        return DTOMapper.toDTO(staff);
    }

    // ---------------- assignment helpers ----------------

    // AVAILABLE -> BUSY atomically. false = someone else took this person first (skip them)
    @Transactional
    public boolean tryClaim(Staff staff) {
        int updated = staffRepository.transitionStatus(staff.getId(), StaffStatus.AVAILABLE, StaffStatus.BUSY);
        if (updated == 0) return false;
        entityManager.refresh(staff); // bulk update bypasses the persistence context (status + @Version)
        broadcast(staff);
        return true;
    }

    // back to AVAILABLE only when the person has no other open task (and was not set OFF_DUTY meanwhile)
    @Transactional
    public void releaseIfIdle(Staff staff) {
        if (staff.getStatus() != StaffStatus.BUSY) return;
        if (taskRepository.existsByStaffIdAndStatusIn(staff.getId(), ACTIVE_TASK)) return;
        staff.setStatus(StaffStatus.AVAILABLE);
        staffRepository.save(staff);
        broadcast(staff);
    }

    public void broadcast(Staff staff) {
        broadcastService.send(BroadcastService.STAFF, "STAFF_UPDATED", DTOMapper.toDTO(staff));
    }

    // ---------------- geometry / shift ----------------

    // current position, or the home zone center when the position is unknown; null if neither exists
    public static double[] positionOf(Staff staff) {
        if (staff.getCurrentX() != null && staff.getCurrentY() != null) {
            return new double[]{staff.getCurrentX(), staff.getCurrentY()};
        }
        if (staff.getHomeZone() != null && staff.getHomeZone().getCenterX() != null
                && staff.getHomeZone().getCenterY() != null) {
            return new double[]{staff.getHomeZone().getCenterX(), staff.getHomeZone().getCenterY()};
        }
        return null;
    }

    // null shift = always on duty; supports overnight shifts (22:00 -> 06:00)
    public static boolean isOnShift(Staff staff, LocalTime now) {
        LocalTime start = staff.getShiftStart();
        LocalTime end = staff.getShiftEnd();
        if (start == null || end == null) return true;
        if (!start.isAfter(end)) return !now.isBefore(start) && !now.isAfter(end);
        return !now.isBefore(start) || !now.isAfter(end);
    }
}
