package com.example.rifd.Controller;

import com.example.rifd.DTOIn.ResolveDTOIn;
import com.example.rifd.DTOIn.SeverityUpdateDTOIn;
import com.example.rifd.DTOIn.StatusUpdateDTOIn;
import com.example.rifd.Enum.IncidentCategory;
import com.example.rifd.Enum.IncidentStatus;
import com.example.rifd.Enum.Severity;
import com.example.rifd.Service.ActivityLogService;
import com.example.rifd.Service.IncidentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/incidents")
@RequiredArgsConstructor
public class IncidentController {

    private final IncidentService incidentService;
    private final ActivityLogService activityLogService;

    @GetMapping
    public ResponseEntity<?> search(@RequestParam(required = false) IncidentStatus status,
                                    @RequestParam(required = false) Severity severity,
                                    @RequestParam(required = false) IncidentCategory category) {
        return ResponseEntity.ok(incidentService.search(status, severity, category));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getDetails(@PathVariable Long id) {
        return ResponseEntity.ok(incidentService.getDetails(id));
    }

    @PostMapping("/{id}/acknowledge")
    public ResponseEntity<?> acknowledge(@PathVariable Long id,
                                         @RequestParam(required = false) Long actorStaffId) {
        return ResponseEntity.ok(incidentService.acknowledge(id, actorStaffId));
    }

    @PatchMapping("/{id}/severity")
    public ResponseEntity<?> updateSeverity(@PathVariable Long id,
                                            @RequestBody @Valid SeverityUpdateDTOIn dto) {
        return ResponseEntity.ok(incidentService.updateSeverity(id, dto.severity(), dto.reason(), dto.actorStaffId()));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id,
                                          @RequestBody @Valid StatusUpdateDTOIn dto) {
        return ResponseEntity.ok(incidentService.updateStatus(id, dto.status(), dto.actorStaffId()));
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<?> resolve(@PathVariable Long id,
                                     @RequestBody(required = false) @Valid ResolveDTOIn dto) {
        return ResponseEntity.ok(dto == null
                ? incidentService.resolve(id, null, null, null)
                : incidentService.resolve(id, dto.resolutionNotes(), dto.actualSeverity(), dto.actorStaffId()));
    }

    @PostMapping("/{id}/false-alarm")
    public ResponseEntity<?> falseAlarm(@PathVariable Long id,
                                        @RequestBody(required = false) @Valid ResolveDTOIn dto) {
        return ResponseEntity.ok(dto == null
                ? incidentService.markFalseAlarm(id, null, null)
                : incidentService.markFalseAlarm(id, dto.resolutionNotes(), dto.actorStaffId()));
    }

    @GetMapping("/{id}/activity")
    public ResponseEntity<?> activity(@PathVariable Long id) {
        return ResponseEntity.ok(activityLogService.getByIncident(id));
    }
}