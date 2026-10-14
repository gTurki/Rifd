package com.example.rifd.Controller;

import com.example.rifd.DTOIn.AlertBatchDTOIn;
import com.example.rifd.DTOIn.AlertDTOIn;
import com.example.rifd.DTOIn.RejectDTOIn;
import com.example.rifd.Enum.AlertStatus;
import com.example.rifd.Security.CurrentUser;
import com.example.rifd.Service.AlertService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.rifd.Telegram.TelegramAttachmentService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;
    private final CurrentUser currentUser;
    private final TelegramAttachmentService telegramAttachmentService;

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        return ResponseEntity.ok(alertService.getById(id));
    }

    // voice / photos from Telegram, streamed through the server: the bot token never reaches the browser
    // album: ?index=0..2 (count = number of ids after "tg:" in attachmentUrl, comma separated)
    @GetMapping("/{id}/attachment")
    public ResponseEntity<byte[]> attachment(@PathVariable Long id,
                                             @RequestParam(defaultValue = "0") int index) {
        TelegramAttachmentService.Attachment file = telegramAttachmentService.getForAlert(id, index);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .body(file.bytes());
    }

    @PostMapping("/{id}/dismiss")
    public ResponseEntity<?> dismiss(@PathVariable Long id, @RequestBody @Valid RejectDTOIn dto) {
        return ResponseEntity.ok(alertService.dismiss(id, dto.reason(), currentUser.actorId(dto.actorStaffId())));
    }

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