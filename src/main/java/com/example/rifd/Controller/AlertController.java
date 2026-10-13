package com.example.rifd.Controller;

import com.example.rifd.DTOIn.AlertBatchDTOIn;
import com.example.rifd.DTOIn.AlertDTOIn;
import com.example.rifd.Enum.AlertStatus;
import com.example.rifd.Service.AlertService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PostMapping
    public ResponseEntity<?> ingest(@RequestBody @Valid AlertDTOIn alert) {
        return ResponseEntity.ok(alertService.ingest(alert));
    }

    @PostMapping("/batch")
    public ResponseEntity<?> ingestBatch(@RequestBody @Valid AlertBatchDTOIn batch) {
        return ResponseEntity.ok(alertService.ingestBatch(batch.alerts()));
    }
}