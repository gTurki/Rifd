package com.example.rifd.Controller;

import com.example.rifd.Service.SimulationService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/simulation")
@RequiredArgsConstructor
public class SimulationController {

    private final SimulationService simulationService;

    // speed=2 -> twice as fast
    @PostMapping("/start")
    public ResponseEntity<?> start(@RequestParam(defaultValue = "1") @DecimalMin("0.1") @DecimalMax("10") double speed) {
        return ResponseEntity.ok(simulationService.start(speed));
    }

    @PostMapping("/reset")
    public ResponseEntity<?> reset() {
        return ResponseEntity.ok(simulationService.reset());
    }

    @GetMapping("/status")
    public ResponseEntity<?> status() {
        return ResponseEntity.ok(simulationService.status());
    }
}
