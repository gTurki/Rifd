package com.example.rifd.Service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;

// UltraMsg: POST /{instance}/messages/chat (token, to, body). Never throws: any failure is logged and skipped.
// The token, the instance id and the message text (it may hold a password) are never logged.
@Slf4j
@Service
public class WhatsAppClient {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SendResponse(String sent, Object error) {
    }

    private final boolean enabled;
    private final String instance;
    private final String token;
    private final RestClient restClient;

    public WhatsAppClient(@Value("${whatsapp.enabled:false}") boolean enabled,
                          @Value("${whatsapp.base-url:https://api.ultramsg.com}") String baseUrl,
                          @Value("${whatsapp.instance:}") String instance,
                          @Value("${whatsapp.token:}") String token,
                          @Value("${whatsapp.timeout-ms:8000}") long timeoutMs) {
        this.enabled = enabled;
        this.instance = instance == null ? "" : instance.trim();
        this.token = token == null ? "" : token.trim();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
        if (enabled && (this.instance.isEmpty() || this.token.isEmpty())) {
            log.warn("whatsapp.enabled=true but ULTRAMSG_INSTANCE / ULTRAMSG_TOKEN is empty: WhatsApp messages are skipped");
        }
    }

    public boolean isEnabled() {
        return enabled && !instance.isEmpty() && !token.isEmpty();
    }

    // @Async: the caller (WhatsAppService, after the commit) never waits for UltraMsg
    @Async
    public void send(String phone, String body) {
        if (!isEnabled()) return;
        String to = normalize(phone);
        if (to == null) {
            log.debug("WhatsApp skipped: no valid phone number");
            return;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", token);
        form.add("to", to);
        form.add("body", body);
        try {
            SendResponse response = restClient.post()
                    .uri("/{instance}/messages/chat", instance)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(SendResponse.class);
            // UltraMsg answers 200 with {"error": ...} for a bad token / disconnected instance
            if (response == null || !"true".equalsIgnoreCase(response.sent())) {
                log.warn("WhatsApp to {} not sent (UltraMsg refused it: check the instance is connected and the token)", mask(to));
            } else {
                log.info("WhatsApp sent to {}", mask(to));
            }
        } catch (RestClientResponseException e) {
            log.warn("WhatsApp to {} failed: HTTP {}", mask(to), e.getStatusCode().value());
        } catch (Exception e) {
            // the message of a connection error can contain the URL (= the instance id): class name only
            log.warn("WhatsApp to {} failed: {}", mask(to), e.getClass().getSimpleName());
        }
    }

    // "05xxxxxxxx" -> "+9665xxxxxxxx", "9665..." -> "+9665...", spaces / dashes removed
    static String normalize(String phone) {
        if (!StringUtils.hasText(phone)) return null;
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.startsWith("00")) digits = digits.substring(2);
        if (digits.length() == 10 && digits.startsWith("05")) digits = "966" + digits.substring(1);
        return digits.length() < 8 ? null : "+" + digits;
    }

    private static String mask(String phone) {
        return phone.length() <= 8 ? "****" : phone.substring(0, 5) + "****" + phone.substring(phone.length() - 4);
    }
}
