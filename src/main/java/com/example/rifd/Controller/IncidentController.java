package com.example.rifd.Controller;

import com.example.rifd.DTOIn.RejectDTOIn;
import com.example.rifd.DTOIn.ResolveDTOIn;
import com.example.rifd.DTOIn.SeverityUpdateDTOIn;
import com.example.rifd.DTOIn.StatusUpdateDTOIn;
import com.example.rifd.Enum.IncidentCategory;
import com.example.rifd.Enum.IncidentStatus;
import com.example.rifd.Enum.Severity;
import com.example.rifd.Security.CurrentUser;
import com.example.rifd.Service.ActivityLogService;
import com.example.rifd.Service.AssignmentService;
import com.example.rifd.Service.RecommendationService;
import com.example.rifd.Service.TaskService;
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
    private final RecommendationService recommendationService;
    private final AssignmentService assignmentService;
    private final TaskService taskService;
    private final CurrentUser currentUser;

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
        return ResponseEntity.ok(incidentService.acknowledge(id, currentUser.actorId(actorStaffId)));
    }

    @PatchMapping("/{id}/severity")
    public ResponseEntity<?> updateSeverity(@PathVariable Long id,
                                            @RequestBody @Valid SeverityUpdateDTOIn dto) {
        return ResponseEntity.ok(incidentService.updateSeverity(id, dto.severity(), dto.reason(), currentUser.actorId(dto.actorStaffId())));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id,
                                          @RequestBody @Valid StatusUpdateDTOIn dto) {
        return ResponseEntity.ok(incidentService.updateStatus(id, dto.status(), currentUser.actorId(dto.actorStaffId())));
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<?> resolve(@PathVariable Long id,
                                     @RequestBody(required = false) @Valid ResolveDTOIn dto) {
        return ResponseEntity.ok(dto == null
                ? incidentService.resolve(id, null, null, currentUser.actorId(null))
                : incidentService.resolve(id, dto.resolutionNotes(), dto.actualSeverity(), currentUser.actorId(dto.actorStaffId())));
    }

    @PostMapping("/{id}/false-alarm")
    public ResponseEntity<?> falseAlarm(@PathVariable Long id,
                                        @RequestBody(required = false) @Valid ResolveDTOIn dto) {
        return ResponseEntity.ok(dto == null
                ? incidentService.markFalseAlarm(id, null, currentUser.actorId(null))
                : incidentService.markFalseAlarm(id, dto.resolutionNotes(), currentUser.actorId(dto.actorStaffId())));
    }

    // body: { "reason": "...", "actorStaffId": 1 } — reason is required
    @PostMapping("/{id}/reopen")
    public ResponseEntity<?> reopen(@PathVariable Long id, @RequestBody @Valid RejectDTOIn dto) {
        return ResponseEntity.ok(incidentService.reopen(id, dto.reason(), currentUser.actorId(dto.actorStaffId())));
    }
    // possible duplicate: "yes, merge it into the original" (alerts move, this incident closes)
    @PostMapping("/{id}/confirm-duplicate")
    public ResponseEntity<?> confirmDuplicate(@PathVariable Long id,
                                              @RequestParam(required = false) Long actorStaffId) {
        return ResponseEntity.ok(incidentService.confirmDuplicate(id, currentUser.actorId(actorStaffId)));
    }

    // possible duplicate: "no, it is a different incident" (only the flag is removed)
    @PostMapping("/{id}/not-duplicate")
    public ResponseEntity<?> notDuplicate(@PathVariable Long id,
                                          @RequestParam(required = false) Long actorStaffId) {
        return ResponseEntity.ok(incidentService.clearDuplicateFlag(id, currentUser.actorId(actorStaffId)));
    }

    @GetMapping("/{id}/activity")
    public ResponseEntity<?> activity(@PathVariable Long id) {
        return ResponseEntity.ok(activityLogService.getByIncident(id));
    }

    @GetMapping("/{id}/recommendations")
    public ResponseEntity<?> recommendations(@PathVariable Long id) {
        return ResponseEntity.ok(recommendationService.byIncident(id));
    }

    // preview of the nearest suitable staff, nothing is assigned
    @GetMapping("/{id}/candidates")
    public ResponseEntity<?> candidates(@PathVariable Long id) {
        return ResponseEntity.ok(assignmentService.candidates(id));
    }

    @GetMapping("/{id}/tasks")
    public ResponseEntity<?> tasks(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.byIncident(id));
    }

    @PostMapping("/{id}/recommendations/regenerate")
    public ResponseEntity<?> regenerateRecommendation(@PathVariable Long id) {
        return ResponseEntity.ok(recommendationService.regenerateNow(id));
    }
}
