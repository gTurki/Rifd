package com.example.rifd.Controller;

import com.example.rifd.Api.ApiException;
import com.example.rifd.DTOIn.LoginDTOIn;
import com.example.rifd.Security.AuthService;
import com.example.rifd.Security.CurrentUser;
import com.example.rifd.DTOIn.PasswordDTOIn;
import com.example.rifd.DTOIn.ProfileDTOIn;
import com.example.rifd.Service.StaffAccountService;
import java.util.Map;
import com.example.rifd.Service.StaffService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CurrentUser currentUser;
    private final StaffService staffService;
    private final StaffAccountService staffAccountService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody @Valid LoginDTOIn dto) {
        return ResponseEntity.ok(authService.login(dto.username(), dto.password()));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me() {
        Long id = currentUser.staffId().orElseThrow(() -> new ApiException("Not logged in"));
        return ResponseEntity.ok(staffService.getById(id));
    }

    @PatchMapping("/me")
    public ResponseEntity<?> updateMe(@RequestBody @Valid ProfileDTOIn dto) {
        Long id = currentUser.staffId().orElseThrow(() -> new ApiException("Not logged in"));
        return ResponseEntity.ok(staffAccountService.updateProfile(id, dto));
    }

    @PostMapping("/me/password")
    public ResponseEntity<?> changePassword(@RequestBody @Valid PasswordDTOIn dto) {
        Long id = currentUser.staffId().orElseThrow(() -> new ApiException("Not logged in"));
        staffAccountService.changePassword(id, dto.currentPassword(), dto.newPassword());
        return ResponseEntity.ok(Map.of("message", "Password changed"));
    }
}
