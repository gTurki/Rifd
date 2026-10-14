package com.example.rifd.Controller;

import com.example.rifd.Service.PlaybookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/playbooks")
@RequiredArgsConstructor
public class PlaybookController {

    private final PlaybookService playbookService;

    @GetMapping
    public ResponseEntity<?> getAll(@RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(playbookService.getAll(active));
    }
}
