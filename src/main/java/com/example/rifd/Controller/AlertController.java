package com.example.rifd.Controller;

import com.example.rifd.Enum.AlertStatus;
import com.example.rifd.Service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @GetMapping
    public ResponseEntity<?> search(@RequestParam(required = false) AlertStatus status,
                                    @RequestParam(required = false) Long incidentId) {
        return ResponseEntity.ok(alertService.search(status, incidentId));
    }
}