package com.example.rifd.Controller;

import com.example.rifd.DTOIn.RejectDTOIn;
import com.example.rifd.Security.CurrentUser;
import com.example.rifd.Telegram.TelegramReporterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/telegram/reporters")
@RequiredArgsConstructor
public class TelegramReporterController {

    private final TelegramReporterService reporterService;
    private final CurrentUser currentUser;

    @GetMapping
    public ResponseEntity<?> search(@RequestParam(required = false) Boolean blocked) {
        return ResponseEntity.ok(reporterService.search(blocked));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        return ResponseEntity.ok(reporterService.getById(id));
    }

    @PostMapping("/{id}/block")
    public ResponseEntity<?> block(@PathVariable Long id, @RequestBody @Valid RejectDTOIn dto) {
        return ResponseEntity.ok(reporterService.block(id, dto.reason(), currentUser.actorId(dto.actorStaffId())));
    }

    @PostMapping("/{id}/unblock")
    public ResponseEntity<?> unblock(@PathVariable Long id, @RequestBody @Valid RejectDTOIn dto) {
        return ResponseEntity.ok(reporterService.unblock(id, dto.reason(), currentUser.actorId(dto.actorStaffId())));
    }
}