package com.example.rifd.Controller;

import com.example.rifd.DTOIn.VisitorReportDTOIn;
import com.example.rifd.Service.VisitorReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// public (no token): the visitor app
@RestController
@RequestMapping("/api/v1/visitor-reports")
@RequiredArgsConstructor
public class VisitorReportController {

    private final VisitorReportService visitorReportService;

    @PostMapping
    public ResponseEntity<?> submit(@RequestBody @Valid VisitorReportDTOIn dto) {
        return ResponseEntity.ok(visitorReportService.submit(dto));
    }

    @GetMapping("/{code}")
    public ResponseEntity<?> track(@PathVariable String code) {
        return ResponseEntity.ok(visitorReportService.track(code));
    }
}