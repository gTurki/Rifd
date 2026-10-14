package com.example.rifd.Controller;

import com.example.rifd.Enum.StaffSpecialty;
import com.example.rifd.Security.CurrentUser;
import com.example.rifd.Service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUser currentUser;

    @GetMapping
    public ResponseEntity<?> search(@RequestParam(required = false) Long staffId,
                                    @RequestParam(required = false) StaffSpecialty specialty,
                                    @RequestParam(defaultValue = "false") boolean unread) {
        // field staff only see their own (+ their specialty's) notifications
        Long effective = currentUser.isField() ? currentUser.staffId().orElse(staffId) : staffId;
        return ResponseEntity.ok(notificationService.search(effective, currentUser.isField() ? null : specialty, unread));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<?> markRead(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.markRead(id));
    }
}
