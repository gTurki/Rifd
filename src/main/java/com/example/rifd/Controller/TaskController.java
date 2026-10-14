package com.example.rifd.Controller;

import com.example.rifd.DTOIn.DecisionDTOIn;
import com.example.rifd.DTOIn.RejectDTOIn;
import com.example.rifd.DTOIn.TaskDTOIn;
import com.example.rifd.DTOIn.TaskStatusDTOIn;
import com.example.rifd.Enum.TaskStatus;
import com.example.rifd.Security.CurrentUser;
import com.example.rifd.Service.AssignmentService;
import com.example.rifd.Service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final AssignmentService assignmentService;
    private final CurrentUser currentUser;

    @GetMapping
    public ResponseEntity<?> search(@RequestParam(required = false) Long staffId,
                                    @RequestParam(required = false) TaskStatus status) {
        return ResponseEntity.ok(taskService.search(staffId, status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.getById(id));
    }

    // manual assignment of a specific staff member
    @PostMapping
    public ResponseEntity<?> assign(@RequestBody @Valid TaskDTOIn dto) {
        return ResponseEntity.ok(assignmentService.assignManual(dto.incidentId(), dto.staffId(), dto.notes(),
                currentUser.actorId(dto.actorStaffId())));
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<?> accept(@PathVariable Long id, @RequestBody(required = false) DecisionDTOIn dto) {
        return ResponseEntity.ok(taskService.accept(id, dto == null ? null : dto.actorStaffId()));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable Long id, @RequestBody @Valid RejectDTOIn dto) {
        return ResponseEntity.ok(taskService.reject(id, dto.reason(), dto.actorStaffId()));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestBody @Valid TaskStatusDTOIn dto) {
        return ResponseEntity.ok(taskService.updateStatus(id, dto.status(), dto.notes(), dto.actorStaffId()));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable Long id, @RequestBody @Valid RejectDTOIn dto) {
        return ResponseEntity.ok(taskService.cancel(id, dto.reason(), dto.actorStaffId()));
    }
}
