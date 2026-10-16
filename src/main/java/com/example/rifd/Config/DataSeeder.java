package com.example.rifd.Config;

import com.example.rifd.Entity.AccessibilityAsset;
import com.example.rifd.Entity.AccessibleRoute;
import com.example.rifd.Entity.Event;
import com.example.rifd.Entity.Playbook;
import com.example.rifd.Entity.Staff;
import com.example.rifd.Entity.Zone;
import com.example.rifd.Enum.AssetStatus;
import com.example.rifd.Enum.AssetType;
import com.example.rifd.Enum.EventType;
import com.example.rifd.Enum.IncidentCategory;
import com.example.rifd.Enum.RouteStatus;
import com.example.rifd.Enum.Severity;
import com.example.rifd.Enum.StaffLevel;
import com.example.rifd.Enum.StaffRole;
import com.example.rifd.Enum.StaffSpecialty;
import com.example.rifd.Enum.StaffStatus;
import com.example.rifd.Enum.ZoneType;
import com.example.rifd.Repository.AccessibilityAssetRepository;
import com.example.rifd.Repository.AccessibleRouteRepository;
import com.example.rifd.Repository.EventRepository;
import com.example.rifd.Repository.PlaybookRepository;
import com.example.rifd.Repository.StaffRepository;
import com.example.rifd.Repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.example.rifd.Enum.StaffLevel.ASSISTANT;
import static com.example.rifd.Enum.StaffLevel.CERTIFIED;
import static com.example.rifd.Enum.StaffSpecialty.*;

// Demo data shaped for the scenario (section 12). Runs only when the DB is empty (always, with create-drop).
// Map (meters, 800 x 600):
//   PD(620,120)          G2(760,300)
//   PA(180,160)  PB(410,200)
//   G1(80,300)   MP(300,300)   PC(560,330)
//   SV(150,450)               TH(700,520)
//          G3(300,560)
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    public static final String DEMO_PASSWORD = "demo123";

    private final ZoneRepository zoneRepository;
    private final EventRepository eventRepository;
    private final StaffRepository staffRepository;
    private final PlaybookRepository playbookRepository;
    private final AccessibilityAssetRepository assetRepository;
    private final AccessibleRouteRepository routeRepository;
    private final PasswordEncoder passwordEncoder;

    private final Map<String, Zone> zones = new HashMap<>();
    private final Map<String, AccessibilityAsset> assets = new HashMap<>();

    @Override
    @Transactional
    public void run(String... args) {
        if (zoneRepository.count() > 0) {
            log.info("DataSeeder skipped: data already present");
            return;
        }
        seedZones();
        seedEvents();
        seedStaff();
        seedPlaybooks();
        seedAssets();
        seedRoutes();
        log.info("DataSeeder: {} zones, {} events, {} staff, {} playbooks, {} assets, {} routes",
                zoneRepository.count(), eventRepository.count(), staffRepository.count(),
                playbookRepository.count(), assetRepository.count(), routeRepository.count());
    }

    // initial position = home zone center + a fixed offset from the employee number (deterministic, so
    // /simulation/reset puts everybody back exactly where the seed put them)
    public static double[] homePosition(Zone home, String employeeNumber) {
        if (home == null || home.getCenterX() == null || home.getCenterY() == null) return null;
        int h = Math.abs(employeeNumber == null ? 0 : employeeNumber.hashCode());
        double dx = (h % 41) - 20;
        double dy = ((h / 41) % 41) - 20;
        return new double[]{home.getCenterX() + dx, home.getCenterY() + dy};
    }

    // ---------------- zones ----------------

    private void seedZones() {
        zone("G1", "Gate 1 - West", ZoneType.GATE, 80, 300, 40, 40, 4000);
        zone("G2", "Gate 2 - East", ZoneType.GATE, 760, 300, 40, 40, 4000);
        zone("G3", "Gate 3 - South", ZoneType.GATE, 300, 560, 40, 30, 4000);
        zone("MP", "Main Plaza", ZoneType.PLAZA, 300, 300, 90, 70, 5000);
        zone("PA", "Pavilion A", ZoneType.PAVILION, 180, 160, 60, 50, 1500);
        zone("PB", "Pavilion B", ZoneType.PAVILION, 410, 200, 60, 50, 1500);
        zone("PC", "Pavilion C", ZoneType.PAVILION, 560, 330, 60, 50, 1500);
        zone("PD", "Pavilion D", ZoneType.PAVILION, 620, 120, 60, 50, 1500);
        zone("TH", "Transport Hub", ZoneType.TRANSPORT_HUB, 700, 520, 70, 50, 3000);
        zone("SV", "Service Area & Food Court", ZoneType.SERVICE_AREA, 150, 450, 60, 50, 800);
    }

    private void zone(String code, String name, ZoneType type, double cx, double cy, double halfW, double halfH, int capacity) {
        Zone z = new Zone();
        z.setCode(code);
        z.setName(name);
        z.setType(type);
        z.setCenterX(cx);
        z.setCenterY(cy);
        z.setCapacity(capacity);
        z.setCurrentOccupancy(0);
        z.setPolygonCoordinates(String.format(Locale.ROOT, "[[%.0f,%.0f],[%.0f,%.0f],[%.0f,%.0f],[%.0f,%.0f]]",
                cx - halfW, cy - halfH, cx + halfW, cy - halfH, cx + halfW, cy + halfH, cx - halfW, cy + halfH));
        zones.put(code, zoneRepository.save(z));
    }

    // ---------------- events ----------------

    private void seedEvents() {
        LocalDateTime now = LocalDateTime.now();
        // live during rehearsals and the demo (SimulationService re-anchors it if it ever ends)
        event("Opening Ceremony", EventType.CEREMONY, "MP", now.minusMinutes(30), now.plusHours(12), 3000);
        event("Cultural Parade", EventType.PARADE, "G1", now.plusHours(14), now.plusHours(16), 1500);
        event("Future Cities Talk", EventType.TALK, "PA", now.minusHours(4), now.minusHours(3), 300);
    }

    private void event(String name, EventType type, String zoneCode, LocalDateTime start, LocalDateTime end, int attendance) {
        Event e = new Event();
        e.setName(name);
        e.setType(type);
        e.setZone(zones.get(zoneCode));
        e.setStartTime(start);
        e.setEndTime(end);
        e.setExpectedAttendance(attendance);
        eventRepository.save(e);
    }

    // ---------------- staff ----------------

    private void seedStaff() {
        String hash = passwordEncoder.encode(DEMO_PASSWORD);

        // control room
        staff("OP-001", "Noura Alqahtani", StaffRole.OPERATOR, null, null, "MP", "operator1", hash);
        staff("OP-002", "Fahad Alharbi", StaffRole.OPERATOR, null, null, "MP", "operator2", hash);
        staff("SUP-001", "Abdullah Alotaibi", StaffRole.SUPERVISOR, null, null, "MP", "supervisor1", hash);

        // crowd management: certified at different distances from the plaza + assistants
        field("C-101", "Saad Alshehri", CROWD_MANAGEMENT, CERTIFIED, "MP", hash);
        field("C-102", "Majed Alzahrani", CROWD_MANAGEMENT, CERTIFIED, "PA", hash);
        field("C-103", "Turki Aldosari", CROWD_MANAGEMENT, CERTIFIED, "TH", hash);
        field("C-201", "Rakan Alanazi", CROWD_MANAGEMENT, ASSISTANT, "MP", hash);
        field("C-202", "Yazeed Alsubaie", CROWD_MANAGEMENT, ASSISTANT, "G3", hash);
        field("C-203", "Hamad Almutairi", CROWD_MANAGEMENT, ASSISTANT, "PC", hash);
        field("C-104", "Sultan Alqahtani", CROWD_MANAGEMENT, CERTIFIED, "G1", hash);
        field("C-105", "Meshal Alotaibi", CROWD_MANAGEMENT, CERTIFIED, "PB", hash);
        // medical
        field("M-101", "Dr. Reem Alghamdi", MEDICAL, CERTIFIED, "PC", hash);
        field("M-102", "Khalid Alshammari", MEDICAL, CERTIFIED, "SV", hash);
        field("M-201", "Sara Alqahtani", MEDICAL, ASSISTANT, "MP", hash);
        // security
        field("S-101", "Omar Alrashid", SECURITY, CERTIFIED, "G1", hash);
        field("S-102", "Nasser Albaqami", SECURITY, CERTIFIED, "PD", hash);
        field("S-201", "Bandar Alenezi", SECURITY, ASSISTANT, "G3", hash);
        field("S-103", "Hatem Aljuhani", SECURITY, CERTIFIED, "MP", hash);
        // fire safety
        field("F-101", "Mansour Alharthi", FIRE_SAFETY, CERTIFIED, "TH", hash);
        field("F-201", "Waleed Alsahli", FIRE_SAFETY, ASSISTANT, "PB", hash);
        field("F-102", "Adel Alshahrani", FIRE_SAFETY, CERTIFIED, "G1", hash);
        field("F-103", "Saleh Albalawi", FIRE_SAFETY, CERTIFIED, "PD", hash);
        field("F-104", "Mohammed Alasmari", FIRE_SAFETY, CERTIFIED, "MP", hash);
        // accessibility team
        field("A-101", "Lama Alsaud", ACCESSIBILITY, CERTIFIED, "PB", hash);
        field("A-201", "Hessa Alamri", ACCESSIBILITY, ASSISTANT, "MP", hash);
        // technical (elevators, power, screens)
        field("T-101", "Faisal Alyami", TECHNICAL, CERTIFIED, "PB", hash);
        field("T-102", "Ibrahim Alkhaldi", TECHNICAL, CERTIFIED, "G2", hash);
        field("T-201", "Ziyad Alruwaili", TECHNICAL, ASSISTANT, "SV", hash);
        // visitor services (lost & found, cleanliness, help desk)
        field("V-101", "Maha Alfaisal", VISITOR_SERVICES, CERTIFIED, "G1", hash);
        field("V-201", "Abeer Alsulami", VISITOR_SERVICES, ASSISTANT, "SV", hash);
        field("V-202", "Nawaf Alhazmi", VISITOR_SERVICES, ASSISTANT, "PA", hash);
    }

    private void field(String number, String name, StaffSpecialty specialty, StaffLevel level, String home, String hash) {
        staff(number, name, StaffRole.FIELD, specialty, level, home, number, hash);
    }

    private void staff(String number, String name, StaffRole role, StaffSpecialty specialty, StaffLevel level,
                       String home, String username, String hash) {
        Staff s = new Staff();
        s.setEmployeeNumber(number);
        s.setName(name);
        s.setPhone("+966549931017"); // demo: every seeded account gets the team's WhatsApp number
        s.setRole(role);
        s.setSpecialty(specialty);
        s.setLevel(level);
        s.setStatus(StaffStatus.AVAILABLE);
        Zone zone = zones.get(home);
        s.setHomeZone(zone);
        double[] pos = homePosition(zone, number);
        s.setCurrentX(pos[0]);
        s.setCurrentY(pos[1]);
        s.setLastLocationUpdate(LocalDateTime.now());
        // whole day; 23:59:59 so the last minute of the day is not "off shift"
        s.setShiftStart(LocalTime.MIDNIGHT);
        s.setShiftEnd(LocalTime.of(23, 59, 59));
        s.setUsername(username);
        s.setPasswordHash(hash);
        staffRepository.save(s);
    }

    // ---------------- playbooks ----------------

    // loaded from resources/seed/playbook.csv (UTF-8 with BOM, quoted multi-line steps)
    private void seedPlaybooks() {
        String content;
        try (InputStream in = new ClassPathResource("seed/playbook.csv").getInputStream()) {
            content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read seed/playbook.csv", e);
        }
        if (content.startsWith("\uFEFF")) content = content.substring(1);

        List<List<String>> rows = parseCsv(content);
        List<String> header = rows.get(0);

        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (row.size() == 1 && row.get(0).isBlank()) continue;

            Map<String, String> v = new HashMap<>();
            for (int c = 0; c < header.size(); c++) {
                v.put(header.get(c).trim(), c < row.size() ? row.get(c).trim() : "");
            }

            Playbook p = new Playbook();
            p.setTitle(v.get("title"));
            p.setCategory(IncidentCategory.valueOf(v.get("category")));
            p.setMinSeverity(Severity.valueOf(v.get("minSeverity")));
            p.setRequiredSpecialty(StaffSpecialty.valueOf(v.get("requiredSpecialty")));
            p.setRequiredLevel(StaffLevel.valueOf(v.get("requiredLevel")));
            p.setRecommendedStaffCount(Integer.parseInt(v.get("recommendedStaffCount")));
            p.setTargetResponseMinutes(Integer.parseInt(v.get("targetResponseMinutes")));
            p.setSteps(v.get("steps"));
            p.setIsActive(!"false".equalsIgnoreCase(v.get("isActive")));
            playbookRepository.save(p);
        }
    }

    // minimal CSV parser: commas, "quoted" fields, "" escapes, line breaks inside quotes
    private static List<List<String>> parseCsv(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else if (ch != '\r') {
                    field.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (ch == '\n') {
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else if (ch != '\r') {
                field.append(ch);
            }
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }

    // ---------------- accessibility assets ----------------

    private void seedAssets() {
        // the elevator that breaks in the scenario: same spot as the demo alerts (~410, 200)
        asset("Elevator B2", AssetType.ELEVATOR, "PB", 412, 202);
        asset("Elevator A1", AssetType.ELEVATOR, "PA", 185, 165);
        asset("Elevator C1", AssetType.ELEVATOR, "PC", 560, 335);
        asset("Ramp MP-North", AssetType.RAMP, "MP", 300, 245);
        asset("Ramp G1", AssetType.RAMP, "G1", 100, 300);
        asset("Accessible Entrance G3", AssetType.ACCESSIBLE_ENTRANCE, "G3", 300, 540);
        asset("Accessible Toilet SV", AssetType.ACCESSIBLE_TOILET, "SV", 165, 440);
        asset("Shuttle Stop TH", AssetType.SHUTTLE, "TH", 690, 510);
    }

    private void asset(String name, AssetType type, String zoneCode, double x, double y) {
        AccessibilityAsset a = new AccessibilityAsset();
        a.setName(name);
        a.setType(type);
        a.setZone(zones.get(zoneCode));
        a.setX(x);
        a.setY(y);
        a.setStatus(AssetStatus.OPERATIONAL);
        assets.put(name, assetRepository.save(a));
    }

    // ---------------- accessible routes ----------------
    // MP -> PB normally uses Elevator B2 (160 m). When it breaks, Dijkstra finds MP -> PA -> PB (430 m)
    // before MP -> PC -> PB (470 m).

    private void seedRoutes() {
        route("Main Plaza → Pavilion B (Elevator B2)", "MP", "PB", 160, 3, "Elevator B2");
        route("Main Plaza → Pavilion A (north ramp)", "MP", "PA", 190, 4, "Ramp MP-North");
        route("Pavilion A → Pavilion B (skybridge)", "PA", "PB", 240, 5, "Elevator A1");
        route("Main Plaza → Pavilion C", "MP", "PC", 270, 5, "Elevator C1");
        route("Pavilion C → Pavilion B", "PC", "PB", 200, 4);
        route("Gate 1 → Main Plaza", "G1", "MP", 230, 4, "Ramp G1");
        route("Gate 3 → Main Plaza", "G3", "MP", 270, 5, "Accessible Entrance G3");
        route("Main Plaza → Service Area", "MP", "SV", 220, 4);
        route("Pavilion C → Transport Hub", "PC", "TH", 240, 5, "Shuttle Stop TH");
    }

    private void route(String name, String from, String to, double length, int minutes, String... assetNames) {
        Zone a = zones.get(from);
        Zone b = zones.get(to);
        AccessibleRoute r = new AccessibleRoute();
        r.setName(name);
        r.setFromZone(a);
        r.setToZone(b);
        r.setLengthMeters(length);
        r.setEstimatedMinutes(minutes);
        r.setStatus(RouteStatus.ACTIVE);
        r.setPathCoordinates(String.format(Locale.ROOT, "[[%.0f,%.0f],[%.0f,%.0f]]",
                a.getCenterX(), a.getCenterY(), b.getCenterX(), b.getCenterY()));
        List<AccessibilityAsset> list = new ArrayList<>();
        for (String n : assetNames) list.add(assets.get(n));
        r.setAssets(list);
        r.setLastUpdated(LocalDateTime.now());
        routeRepository.save(r);
    }
}
