package com.example.rifd.Telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.util.StringUtils.hasText;

@Slf4j
@Component
public class TelegramClient {

    // ---- Telegram Bot API types (only the fields we use) ----

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TgResponse<T>(boolean ok, T result, String description) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Update(@JsonProperty("update_id") long updateId,
                         Message message,
                         @JsonProperty("callback_query") CallbackQuery callbackQuery) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record User(long id,
                       @JsonProperty("is_bot") boolean isBot,
                       @JsonProperty("first_name") String firstName,
                       @JsonProperty("username") String username) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chat(long id, String type) {
        public boolean isPrivate() {
            return "private".equals(type);
        }
    }

    // used for both "voice" (recorded in the app) and "audio" (an uploaded audio file)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Voice(@JsonProperty("file_id") String fileId,
                        @JsonProperty("duration") Integer duration,
                        @JsonProperty("mime_type") String mimeType,
                        @JsonProperty("file_size") Long fileSize) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PhotoSize(@JsonProperty("file_id") String fileId,
                            @JsonProperty("width") Integer width,
                            @JsonProperty("height") Integer height,
                            @JsonProperty("file_size") Long fileSize) {
    }

    // a file sent "as a file" (uncompressed photos come this way)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Document(@JsonProperty("file_id") String fileId,
                           @JsonProperty("file_name") String fileName,
                           @JsonProperty("mime_type") String mimeType,
                           @JsonProperty("file_size") Long fileSize) {
        public boolean isImage() {
            return mimeType != null && mimeType.startsWith("image/");
        }
    }

    // shared with the request_contact button. user_id tells whose number it is
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Contact(@JsonProperty("phone_number") String phoneNumber,
                          @JsonProperty("first_name") String firstName,
                          @JsonProperty("user_id") Long userId) {
    }

    // the image to analyze in a message, whatever way it was sent
    public record ImageRef(String fileId, String mimeType, Long fileSize) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(@JsonProperty("message_id") long messageId,
                          User from,
                          Chat chat,
                          long date,
                          String text,
                          String caption,
                          @JsonProperty("media_group_id") String mediaGroupId,
                          List<PhotoSize> photo,
                          Voice voice,
                          Voice audio,
                          Object video,
                          @JsonProperty("video_note") Object videoNote,
                          Document document,
                          Object sticker,
                          Object animation,
                          Object location,
                          Contact contact) {

        public boolean hasMedia() {
            return photo != null || voice != null || audio != null || video != null || videoNote != null
                    || document != null || sticker != null || animation != null || location != null;
        }

        // photo: Telegram sends several sizes, the last one is the largest. Or an image sent as a file. null if none
        public ImageRef image() {
            if (photo != null && !photo.isEmpty()) {
                PhotoSize largest = photo.get(photo.size() - 1);
                return new ImageRef(largest.fileId(), "image/jpeg", largest.fileSize());
            }
            if (document != null && document.isImage()) {
                return new ImageRef(document.fileId(), document.mimeType(), document.fileSize());
            }
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CallbackQuery(String id, User from, Message message, String data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TgFile(@JsonProperty("file_id") String fileId,
                         @JsonProperty("file_size") Long fileSize,
                         @JsonProperty("file_path") String filePath) {
    }

    // downloaded file + its Telegram path (the extension tells the content type)
    public record TgDownload(byte[] bytes, String filePath) {
    }

    // ---- reply markups ----

    // buttons under a message
    public record Button(String text, @JsonProperty("callback_data") String callbackData) {
    }

    public record InlineKeyboard(@JsonProperty("inline_keyboard") List<List<Button>> inlineKeyboard) {
    }

    // buttons in place of the phone keyboard (needed for request_contact)
    public record KeyboardButton(String text, @JsonProperty("request_contact") boolean requestContact) {
    }

    public record ReplyKeyboard(@JsonProperty("keyboard") List<List<KeyboardButton>> keyboard,
                                @JsonProperty("resize_keyboard") boolean resizeKeyboard,
                                @JsonProperty("one_time_keyboard") boolean oneTimeKeyboard) {
    }

    public record RemoveKeyboard(@JsonProperty("remove_keyboard") boolean removeKeyboard) {
    }

    // ---- client ----

    private final String token;
    private final String fileBaseUrl;
    private final RestClient restClient;

    public TelegramClient(@Value("${telegram.bot-token:}") String token,
                          @Value("${telegram.base-url:https://api.telegram.org}") String baseUrl,
                          @Value("${telegram.poll-timeout-seconds:30}") int pollTimeoutSeconds) {
        this.token = token == null ? "" : token.trim();
        // file downloads use a different path: /file/bot<token>/<file_path>
        this.fileBaseUrl = baseUrl + "/file/bot" + this.token;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        // long polling keeps getUpdates open for pollTimeout seconds: read timeout must be longer
        requestFactory.setReadTimeout(Duration.ofSeconds(pollTimeoutSeconds + 15L));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl + "/bot" + this.token)
                .requestFactory(requestFactory)
                .build();
    }

    public boolean isConfigured() {
        return hasText(token);
    }

    // throws on failure: the polling loop backs off and retries
    public List<Update> getUpdates(long offset, int timeoutSeconds) {
        Map<String, Object> body = new HashMap<>();
        body.put("offset", offset);
        body.put("timeout", timeoutSeconds);
        body.put("allowed_updates", List.of("message", "callback_query"));

        TgResponse<List<Update>> response = call("getUpdates", body,
                new ParameterizedTypeReference<TgResponse<List<Update>>>() {
                });
        return response.result() == null ? List.of() : response.result();
    }

    // replyMarkup: InlineKeyboard, ReplyKeyboard, RemoveKeyboard or null.
    // Returns the sent message id, or null on failure (never throws)
    public Long sendMessage(long chatId, String text, Long replyToMessageId, Object replyMarkup) {
        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);
        if (replyToMessageId != null) {
            body.put("reply_parameters", Map.of("message_id", replyToMessageId, "allow_sending_without_reply", true));
        }
        if (replyMarkup != null) body.put("reply_markup", replyMarkup);

        try {
            Message sent = call("sendMessage", body,
                    new ParameterizedTypeReference<TgResponse<Message>>() {
                    }).result();
            return sent == null ? null : sent.messageId();
        } catch (Exception e) {
            log.warn("Telegram sendMessage to chat {} failed: {}", chatId, safeMessage(e));
            return null;
        }
    }

    // no reply_markup -> the inline keyboard is removed (no double clicks)
    public void editMessageText(long chatId, long messageId, String text) {
        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("text", text);
        try {
            call("editMessageText", body, new ParameterizedTypeReference<TgResponse<Object>>() {
            });
        } catch (Exception e) {
            log.warn("Telegram editMessageText in chat {} failed: {}", chatId, safeMessage(e));
        }
    }

    // stops the loading spinner on the button; text (optional) shows as a small toast
    public void answerCallbackQuery(String callbackQueryId, String text) {
        Map<String, Object> body = new HashMap<>();
        body.put("callback_query_id", callbackQueryId);
        if (hasText(text)) body.put("text", text);
        try {
            call("answerCallbackQuery", body, new ParameterizedTypeReference<TgResponse<Boolean>>() {
            });
        } catch (Exception e) {
            log.warn("Telegram answerCallbackQuery failed: {}", safeMessage(e));
        }
    }

    // "typing..." indicator for ~5 seconds while we work (best effort)
    public void sendChatAction(long chatId, String action) {
        try {
            call("sendChatAction", Map.of("chat_id", chatId, "action", action),
                    new ParameterizedTypeReference<TgResponse<Boolean>>() {
                    });
        } catch (Exception e) {
            log.debug("Telegram sendChatAction failed: {}", safeMessage(e));
        }
    }

    // file bytes in memory only (the download URL contains the token: never stored, decision 72). null on failure
    public TgDownload downloadFile(String fileId, long maxBytes) {
        try {
            TgFile file = call("getFile", Map.of("file_id", fileId),
                    new ParameterizedTypeReference<TgResponse<TgFile>>() {
                    }).result();

            if (file == null || !hasText(file.filePath())) {
                log.warn("Telegram getFile returned no path for {}", fileId);
                return null;
            }
            if (file.fileSize() != null && file.fileSize() > maxBytes) {
                log.warn("Telegram file {} too large: {} bytes", fileId, file.fileSize());
                return null;
            }

            // absolute URI: the base URL (/bot<token>) is not applied
            byte[] bytes = restClient.get()
                    .uri(URI.create(fileBaseUrl + "/" + file.filePath()))
                    .retrieve()
                    .body(byte[].class);
            return bytes == null ? null : new TgDownload(bytes, file.filePath());
        } catch (Exception e) {
            log.warn("Telegram file download failed: {}", safeMessage(e));
            return null;
        }
    }

    // error messages can contain the request URL, and the URL contains the token
    public String safeMessage(Exception e) {
        String message = e.getMessage();
        if (message == null) return e.getClass().getSimpleName();
        return hasText(token) ? message.replace(token, "***") : message;
    }

    private <T> TgResponse<T> call(String method, Map<String, Object> body,
                                   ParameterizedTypeReference<TgResponse<T>> type) {
        TgResponse<T> response = restClient.post()
                .uri("/" + method)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(type);

        if (response == null || !response.ok()) {
            throw new IllegalStateException(method + " failed: "
                    + (response == null ? "empty response" : response.description()));
        }
        return response;
    }
}