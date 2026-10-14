package com.example.rifd.Controller;

import com.example.rifd.DTOIn.LocationDTOIn;
import com.example.rifd.DTOIn.StaffStatusDTOIn;
import com.example.rifd.Enum.StaffRole;
import com.example.rifd.Enum.StaffSpecialty;
import com.example.rifd.Enum.StaffStatus;
import com.example.rifd.Security.CurrentUser;
import com.example.rifd.Service.StaffService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/staff")
@RequiredArgsConstructor
public class StaffController {

    private final StaffService staffService;
    private final CurrentUser currentUser;

    @GetMapping
    public ResponseEntity<?> search(@RequestParam(required = false) StaffStatus status,
                                    @RequestParam(required = false) StaffSpecialty specialty,
                                    @RequestParam(required = false) StaffRole role) {
        return ResponseEntity.ok(staffService.search(status, specialty, role));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        currentUser.requireSelfIfField(id);
        return ResponseEntity.ok(staffService.getById(id));
    }

    @PatchMapping("/{id}/location")
    public ResponseEntity<?> updateLocation(@PathVariable Long id, @RequestBody @Valid LocationDTOIn dto) {
        currentUser.requireSelfIfField(id);
        return ResponseEntity.ok(staffService.updateLocation(id, dto.x(), dto.y()));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestBody @Valid StaffStatusDTOIn dto) {
        return ResponseEntity.ok(staffService.updateStatus(id, dto.status()));
    }
}
