package com.example.rifd.Controller;

import com.example.rifd.DTOIn.ReportConfirmDTOIn;
import com.example.rifd.DTOIn.VisitorReportDTOIn;
import com.example.rifd.Service.VisitorReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

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

    // same as above with up to 3 photos and / or one voice note (WAV). text is optional when a file is attached
    @PostMapping(value = "/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> submitMedia(@RequestParam(required = false) String text,
                                         @RequestParam(required = false) String language,
                                         @RequestParam(required = false) Double x,
                                         @RequestParam(required = false) Double y,
                                         @RequestParam(required = false) String zoneCode,
                                         @RequestParam(required = false) List<MultipartFile> photos,
                                         @RequestParam(required = false) MultipartFile audio) {
        return ResponseEntity.ok(visitorReportService.submitMedia(text, language, x, y, zoneCode, photos, audio));
    }

    // step 1: analyze photos / voice note and find the zone; nothing is reported yet
    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> preview(@RequestParam(required = false) String text,
                                     @RequestParam(required = false) String language,
                                     @RequestParam(required = false) Double x,
                                     @RequestParam(required = false) Double y,
                                     @RequestParam(required = false) String zoneCode,
                                     @RequestParam(required = false) List<MultipartFile> photos,
                                     @RequestParam(required = false) MultipartFile audio) {
        return ResponseEntity.ok(visitorReportService.preview(text, language, x, y, zoneCode, photos, audio));
    }

    // step 2: the visitor checked what we heard and the place -> the report is sent
    @PostMapping("/confirm")
    public ResponseEntity<?> confirm(@RequestBody @Valid ReportConfirmDTOIn dto) {
        return ResponseEntity.ok(visitorReportService.confirm(dto));
    }

    @GetMapping("/{code}")
    public ResponseEntity<?> track(@PathVariable String code) {
        return ResponseEntity.ok(visitorReportService.track(code));
    }
}