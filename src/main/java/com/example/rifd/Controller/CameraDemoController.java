package com.example.rifd.Controller;

import com.example.rifd.DTOOut.AlertDTOOut;
import com.example.rifd.Service.CameraDemoService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/demo/camera-alerts")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "camera-demo.enabled", havingValue = "true")
public class CameraDemoController {

    private final CameraDemoService cameraDemoService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AlertDTOOut submit(@RequestParam MultipartFile photo,
                              @RequestParam String cameraId,
                              @RequestParam String zoneCode,
                              @RequestParam int peopleCount,
                              @RequestParam int threshold,
                              @RequestParam UUID eventId) {
        return cameraDemoService.submit(photo, cameraId, zoneCode, peopleCount, threshold, eventId);
    }
}
