package com.example.rifd.Controller;

import com.example.rifd.DTOIn.DecisionDTOIn;
import com.example.rifd.DTOIn.RejectDTOIn;
import com.example.rifd.Security.CurrentUser;
import com.example.rifd.Service.RecommendationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final CurrentUser currentUser;

    // body optional: { "actorStaffId": 2 }
    @PostMapping("/{id}/accept")
    public ResponseEntity<?> accept(@PathVariable Long id, @RequestBody(required = false) DecisionDTOIn dto) {
        Long actor = currentUser.actorId(dto == null ? null : dto.actorStaffId());
        return ResponseEntity.ok(recommendationService.accept(id, actor));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable Long id, @RequestBody @Valid RejectDTOIn dto) {
        return ResponseEntity.ok(recommendationService.reject(id, dto.reason(), currentUser.actorId(dto.actorStaffId())));
    }
}
