package com.example.rifd.Telegram;

import com.example.rifd.Api.ApiException;
import com.example.rifd.Entity.Alert;
import com.example.rifd.Repository.AlertRepository;
import com.example.rifd.Service.SimulationService;
import com.example.rifd.Telegram.TelegramClient.TgDownload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;

import static org.springframework.util.StringUtils.hasText;

// serves alert attachments to the dashboard:
// - tg:<id>[,<id2>,<id3>] = Telegram voice / photos, fetched on demand (nothing stored, the token never leaves the server)
// - scenario:<file>       = a camera frame of the fire scenario, from resources/scenario/images
@Service
@RequiredArgsConstructor
public class TelegramAttachmentService {

    public record Attachment(byte[] bytes, String contentType) {
    }

    private static final String PREFIX = "tg:";
    private static final long MAX_BYTES = 20L * 1024 * 1024;

    private final AlertRepository alertRepository;
    private final TelegramClient client;

    // index picks which file of an album (0 = first)
    // no transaction on purpose: no DB connection held during the Telegram download
    public Attachment getForAlert(Long alertId, int index) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ApiException("Alert not found"));

        String url = alert.getAttachmentUrl();
        if (!hasText(url)) throw new ApiException("Alert has no attachment");

        if (url.startsWith(SimulationService.IMAGE_PREFIX)) {
            if (index != 0) throw new ApiException("Attachment index out of range");
            String name = url.substring(SimulationService.IMAGE_PREFIX.length());
            byte[] bytes = SimulationService.readImage(name);
            if (bytes == null) throw new ApiException("Scenario image not found");
            return new Attachment(bytes, SimulationService.imageType(name));
        }

        if (!url.startsWith(PREFIX)) throw new ApiException("Unknown attachment type");
        if (!client.isConfigured()) throw new ApiException("Telegram is not configured");

        String[] fileIds = url.substring(PREFIX.length()).split(",");
        if (index < 0 || index >= fileIds.length) throw new ApiException("Attachment index out of range");

        TgDownload file = client.downloadFile(fileIds[index].trim(), MAX_BYTES);
        if (file == null) throw new ApiException("Could not fetch the attachment from Telegram");

        return new Attachment(file.bytes(), contentType(file.filePath()));
    }

    private static String contentType(String filePath) {
        String path = filePath.toLowerCase(Locale.ROOT);
        if (path.endsWith(".oga") || path.endsWith(".ogg") || path.endsWith(".opus")) return "audio/ogg";
        if (path.endsWith(".mp3")) return "audio/mpeg";
        if (path.endsWith(".m4a")) return "audio/mp4";
        if (path.endsWith(".wav")) return "audio/wav";
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }
}