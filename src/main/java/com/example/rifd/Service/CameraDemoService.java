package com.example.rifd.Service;

import com.example.rifd.Api.ApiException;
import com.example.rifd.DTOIn.AlertDTOIn;
import com.example.rifd.DTOOut.AlertDTOOut;
import com.example.rifd.Entity.Alert;
import com.example.rifd.Entity.MediaFile;
import com.example.rifd.Enum.AlertSource;
import com.example.rifd.Enum.AlertStatus;
import com.example.rifd.Repository.AlertRepository;
import com.example.rifd.Repository.MediaFileRepository;
import com.example.rifd.Repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "camera-demo.enabled", havingValue = "true")
public class CameraDemoService {

    private static final long MAX_PHOTO_BYTES = 5L * 1024 * 1024;
    private static final long MAX_PIXELS = 12_000_000;
    private final MediaFileRepository mediaFileRepository;
    private final AlertRepository alertRepository;
    private final ZoneRepository zoneRepository;
    private final AlertService alertService;

    // One local demo process; serialize duplicate event IDs before saving any attachments.
    // sourceReference also lets a completed request be recovered after a lost HTTP response.
    public synchronized AlertDTOOut submit(MultipartFile photo, String cameraId, String zoneCode,
                                           int peopleCount, int threshold, UUID eventId) {
        if (cameraId == null || !cameraId.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new ApiException("cameraId must contain 1–64 letters, digits, underscores or hyphens");
        }
        if (threshold < 1 || threshold > 100 || peopleCount < threshold || peopleCount > 100) {
            throw new ApiException("Counts must be between 1 and 100 and peopleCount must reach threshold");
        }
        if (eventId == null) throw new ApiException("eventId is required");
        String zone = zoneCode == null ? "" : zoneCode.trim().toUpperCase(Locale.ROOT);
        if (zone.isEmpty() || zone.length() > 20 || zoneRepository.findByCode(zone).isEmpty()) {
            throw new ApiException("Select an existing zone before submitting a camera alert");
        }

        String reference = "camera-demo:" + cameraId + ":" + eventId;
        Optional<Alert> previous = alertRepository.findFirstBySourceReference(reference);
        if (previous.isPresent()) {
            if (previous.get().getStatus() == AlertStatus.NEW) {
                throw new ApiException("This event was received but has not finished processing; check the dashboard");
            }
            return alertService.getById(previous.get().getId());
        }

        byte[] bytes = readJpeg(photo);
        MediaFile file = mediaFileRepository.save(new MediaFile(bytes, "image/jpeg"));
        String text = String.format(Locale.ROOT,
                "Camera demo crowd alert: %d visible people; configured demo threshold %d. Camera %s, zone %s. "
                        + "Triggered by local person detection and a count rule; no server-side image AI verification.",
                peopleCount, threshold, cameraId, zone);
        AlertDTOIn alert = new AlertDTOIn(AlertSource.CCTV, reference, text, "en",
                MediaReportService.PREFIX + file.getId(), null, null, zone, null);
        try {
            return alertService.ingestCameraDemo(alert);
        } catch (RuntimeException failure) {
            // Preserve the attachment if the raw NEW alert was already committed.
            try {
                if (alertRepository.findFirstBySourceReference(reference).isEmpty()) {
                    mediaFileRepository.deleteById(file.getId());
                }
            } catch (RuntimeException cleanupFailure) {
                log.warn("Could not clean up camera demo attachment {}", file.getId(), cleanupFailure);
            }
            throw failure;
        }
    }

    private byte[] readJpeg(MultipartFile photo) {
        if (photo == null || photo.isEmpty()) throw new ApiException("A camera screenshot is required");
        if (photo.getSize() > MAX_PHOTO_BYTES) throw new ApiException("Screenshot must be at most 5 MiB");
        if (!"image/jpeg".equalsIgnoreCase(photo.getContentType())) {
            throw new ApiException("Screenshot must be a JPEG image");
        }
        try {
            byte[] bytes = photo.getBytes();
            if (bytes.length > MAX_PHOTO_BYTES) throw new ApiException("Screenshot must be at most 5 MiB");
            try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw new ApiException("Screenshot is not a readable JPEG image");
                ImageReader reader = readers.next();
                try {
                    reader.setInput(input, true, true);
                    if (!"JPEG".equalsIgnoreCase(reader.getFormatName())) {
                        throw new ApiException("Screenshot contents must be JPEG");
                    }
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    if (width < 1 || height < 1 || (long) width * height > MAX_PIXELS) {
                        throw new ApiException("Screenshot must contain at most 12 million pixels");
                    }
                    if (reader.read(0) == null) throw new ApiException("Screenshot could not be decoded");
                } finally {
                    reader.dispose();
                }
            }
            return bytes;
        } catch (IOException | IllegalArgumentException exception) {
            throw new ApiException("Screenshot could not be decoded as JPEG");
        }
    }
}
