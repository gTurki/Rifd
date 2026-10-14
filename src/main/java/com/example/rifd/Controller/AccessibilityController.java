package com.example.rifd.Controller;

import com.example.rifd.DTOIn.AssetStatusDTOIn;
import com.example.rifd.Security.CurrentUser;
import com.example.rifd.Service.AccessibilityService;
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

    @GetMapping("/assets")
    public ResponseEntity<?> assets() {
        return ResponseEntity.ok(accessibilityService.getAssets());
    }

    @PatchMapping("/assets/{id}/status")
    public ResponseEntity<?> updateAssetStatus(@PathVariable Long id, @RequestBody @Valid AssetStatusDTOIn dto) {
        return ResponseEntity.ok(accessibilityService.updateAssetStatus(id, dto.status(),
                currentUser.actorId(dto.actorStaffId())));
    }

    @GetMapping("/routes")
    public ResponseEntity<?> routes() {
        return ResponseEntity.ok(accessibilityService.getRoutes());
    }

    @GetMapping("/routes/{id}/alternative")
    public ResponseEntity<?> alternative(@PathVariable Long id) {
        return ResponseEntity.ok(accessibilityService.getAlternative(id));
    }
}
