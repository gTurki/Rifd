package com.example.rifd.Service;

import com.example.rifd.Api.ApiException;
import com.example.rifd.Config.DataSeeder;
import com.example.rifd.DTOIn.ProfileDTOIn;
import com.example.rifd.DTOIn.StaffCreateDTOIn;
import com.example.rifd.DTOOut.StaffCreatedDTOOut;
import com.example.rifd.DTOOut.StaffDTOOut;
import com.example.rifd.Entity.Staff;
import com.example.rifd.Entity.Zone;
import com.example.rifd.Enum.StaffRole;
import com.example.rifd.Enum.StaffStatus;
import com.example.rifd.Mapper.DTOMapper;
import com.example.rifd.Repository.StaffRepository;
import com.example.rifd.Repository.ZoneRepository;
import com.example.rifd.Security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// accounts (supervisor) + own profile (everyone)
@Service
@RequiredArgsConstructor
public class StaffAccountService {

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SYMBOLS = "@#$%&*";
    private static final String USER_LETTERS = "abcdefghjkmnpqrstuvwxyz";

    private final SecureRandom random = new SecureRandom();

    private final StaffRepository staffRepository;
    private final ZoneRepository zoneRepository;
    private final PasswordEncoder passwordEncoder;
    private final StaffService staffService;
    private final BroadcastService broadcastService;
    private final CurrentUser currentUser;

    // ---------------- accounts ----------------

    @Transactional
    public StaffCreatedDTOOut create(StaffCreateDTOIn dto) {
        boolean field = dto.role() == StaffRole.FIELD;
        if (field && (dto.specialty() == null || dto.level() == null)) {
            throw new ApiException("Field staff need a specialty and a level");
        }
        if ((dto.shiftStart() == null) != (dto.shiftEnd() == null)) {
            throw new ApiException("Send both shiftStart and shiftEnd, or neither");
        }
        Zone zone = zoneRepository.findByCode(dto.homeZoneCode().trim())
                .orElseThrow(() -> new ApiException("Zone not found"));

        String number = uniqueEmployeeNumber();
        String username = uniqueUsername(switch (dto.role()) {
            case SUPERVISOR -> "Sup-Expo-";
            case OPERATOR -> "Op-Expo-";
            default -> "Emp-Expo-";
        });
        String password = generatePassword();

        Staff s = new Staff();
        s.setEmployeeNumber(number);
        s.setName(dto.name().trim());
        s.setPhone(blankToNull(dto.phone()));
        s.setEmail(blankToNull(dto.email()));
        s.setRole(dto.role());
        s.setSpecialty(field ? dto.specialty() : null);
        s.setLevel(field ? dto.level() : null);
        s.setStatus(StaffStatus.AVAILABLE);
        s.setHomeZone(zone);
        double[] pos = DataSeeder.homePosition(zone, number);
        if (pos != null) {
            s.setCurrentX(pos[0]);
            s.setCurrentY(pos[1]);
            s.setLastLocationUpdate(LocalDateTime.now());
        }
        s.setShiftStart(dto.shiftStart() == null ? LocalTime.MIDNIGHT : dto.shiftStart());
        s.setShiftEnd(dto.shiftEnd() == null ? LocalTime.of(23, 59, 59) : dto.shiftEnd());
        s.setUsername(username);
        s.setPasswordHash(passwordEncoder.encode(password));

        staffRepository.save(s);
        staffService.broadcast(s);
        return new StaffCreatedDTOOut(DTOMapper.toDTO(s), username, password);
    }

    // real delete. Anyone with history (tasks, notifications, activity) is protected by the foreign keys:
    // the operator sets them OFF_DUTY instead, so the log stays truthful
    @Transactional
    public void delete(Long staffId) {
        Staff staff = staffService.getStaff(staffId);
        if (Objects.equals(currentUser.staffId().orElse(null), staffId)) {
            throw new ApiException("You cannot delete your own account");
        }
        try {
            staffRepository.delete(staff);
            staffRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ApiException("This account has history (tasks, notifications or activity) and cannot be deleted: set it OFF_DUTY instead");
        }
        broadcastService.send(BroadcastService.STAFF, "STAFF_DELETED", Map.of("id", staffId));
    }

    // ---------------- own profile ----------------

    @Transactional
    public StaffDTOOut updateProfile(Long staffId, ProfileDTOIn dto) {
        Staff staff = staffService.getStaff(staffId);
        staff.setPhone(blankToNull(dto.phone()));
        staff.setEmail(blankToNull(dto.email()));
        staffService.broadcast(staff);
        return DTOMapper.toDTO(staff);
    }

    @Transactional
    public void changePassword(Long staffId, String currentPassword, String newPassword) {
        Staff staff = staffService.getStaff(staffId);
        if (staff.getPasswordHash() == null || !passwordEncoder.matches(currentPassword, staff.getPasswordHash())) {
            throw new ApiException("Current password is incorrect");
        }
        staff.setPasswordHash(passwordEncoder.encode(newPassword));
    }

    // ---------------- generators ----------------

    private String uniqueUsername(String prefix) {
        for (int i = 0; i < 50; i++) {
            String u = prefix + USER_LETTERS.charAt(random.nextInt(USER_LETTERS.length())) + (1000 + random.nextInt(9000));
            if (staffRepository.findByUsername(u).isEmpty()) return u;
        }
        throw new ApiException("Could not generate a username, try again");
    }

    private String uniqueEmployeeNumber() {
        for (int i = 0; i < 50; i++) {
            String n = "E-" + (1000 + random.nextInt(9000));
            if (!staffRepository.existsByEmployeeNumber(n)) return n;
        }
        throw new ApiException("Could not generate an employee number, try again");
    }

    // 10 chars: 2 upper, 4 lower, 3 digits, 1 symbol (same rules as the design)
    private String generatePassword() {
        List<Character> chars = new ArrayList<>();
        add(chars, UPPER, 2);
        add(chars, LOWER, 4);
        add(chars, DIGITS, 3);
        add(chars, SYMBOLS, 1);
        Collections.shuffle(chars, random);
        StringBuilder sb = new StringBuilder();
        chars.forEach(sb::append);
        return sb.toString();
    }

    private void add(List<Character> chars, String set, int count) {
        for (int i = 0; i < count; i++) chars.add(set.charAt(random.nextInt(set.length())));
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}