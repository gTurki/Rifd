package com.example.rifd.Controller;

import com.example.rifd.DTOIn.VisitorLoginDTOIn;
import com.example.rifd.DTOIn.VisitorRegisterDTOIn;
import com.example.rifd.Security.VisitorAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// public: visitor page sign up / sign in
@RestController
@RequestMapping("/api/v1/visitor-auth")
@RequiredArgsConstructor
public class VisitorAuthController {

    private final VisitorAuthService visitorAuthService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody @Valid VisitorRegisterDTOIn dto) {
        return ResponseEntity.ok(visitorAuthService.register(dto));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody @Valid VisitorLoginDTOIn dto) {
        return ResponseEntity.ok(visitorAuthService.login(dto));
    }
}
