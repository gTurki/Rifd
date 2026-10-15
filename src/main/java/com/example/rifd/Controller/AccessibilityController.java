package com.example.rifd.Controller;

import com.example.rifd.DTOIn.AssetStatusDTOIn;
import com.example.rifd.DTOIn.DecisionDTOIn;
import com.example.rifd.Security.CurrentUser;
import com.example.rifd.Service.AccessibilityService;
import com.example.rifd.Service.IncidentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/accessibility")
@RequiredArgsConstructor
public class AccessibilityController {

    private final AccessibilityService accessibilityService;
    private final CurrentUser currentUser;
    private final IncidentService incidentService;

    @GetMapping("/assets")
    public ResponseEntity<?> assets() {
        return ResponseEntity.ok(accessibilityService.getAssets());
    }

    @PatchMapping("/assets/{id}/status")
    public ResponseEntity<?> updateAssetStatus(@PathVariable Long id, @RequestBody @Valid AssetStatusDTOIn dto) {
        Long actor = currentUser.actorId(dto.actorStaffId());
        incidentService.validateOperator(actor);
        return ResponseEntity.ok(accessibilityService.updateAssetStatus(id, dto.status(), actor));
    }

    @GetMapping("/routes")
    public ResponseEntity<?> routes() {
        return ResponseEntity.ok(accessibilityService.getRoutes());
    }

    @GetMapping("/routes/{id}/alternative")
    public ResponseEntity<?> alternative(@PathVariable Long id) {
        return ResponseEntity.ok(accessibilityService.getAlternative(id));
    }

    @PostMapping("/routes/{id}/publish")
    public ResponseEntity<?> publish(@PathVariable Long id, @RequestBody(required = false) DecisionDTOIn dto) {
        Long actor = currentUser.actorId(dto == null ? null : dto.actorStaffId());
        incidentService.validateOperator(actor);
        return ResponseEntity.ok(accessibilityService.publishRoute(id, actor));
    }
}
