package com.example.rifd.Service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

// email through Brevo's HTTPS API (Railway blocks outgoing SMTP, decision 186): POST /v3/smtp/email.
// Never throws: any failure is logged and skipped. The API key and the email body are never logged.
@Slf4j
@Service
public class MailClient {

    private final boolean enabled;
    private final String apiKey;
    private final String from;
    private final String fromName;
    private final RestClient restClient;

    public MailClient(@Value("${mail.enabled:false}") boolean enabled,
                      @Value("${mail.base-url:https://api.brevo.com}") String baseUrl,
                      @Value("${mail.api-key:}") String apiKey,
                      @Value("${mail.from:}") String from,
                      @Value("${mail.from-name:Rifd}") String fromName,
                      @Value("${mail.timeout-ms:8000}") long timeoutMs) {
        this.enabled = enabled;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.from = from == null ? "" : from.trim();
        this.fromName = fromName;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
        if (enabled && (this.apiKey.isEmpty() || this.from.isEmpty())) {
            log.warn("mail.enabled=true but BREVO_API_KEY / MAIL_FROM is empty: emails are skipped");
        }
    }

    public boolean isEnabled() {
        return enabled && !apiKey.isEmpty() && !from.isEmpty();
    }

    // @Async: the caller (MailService, after the commit) never waits for Brevo
    @Async
    public void send(String to, String toName, String subject, String html) {
        if (!isEnabled() || !StringUtils.hasText(to)) return;
        Map<String, Object> body = Map.of(
                "sender", Map.of("name", fromName, "email", from),
                "to", List.of(StringUtils.hasText(toName)
                        ? Map.of("email", to.trim(), "name", toName)
                        : Map.of("email", to.trim())),
                "subject", subject,
                "htmlContent", html);
        try {
            restClient.post()
                    .uri("/v3/smtp/email")
                    .header("api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Email '{}' sent to {}", subject, mask(to));
        } catch (RestClientResponseException e) {
            log.warn("Email to {} failed: HTTP {}", mask(to), e.getStatusCode().value());
        } catch (Exception e) {
            log.warn("Email to {} failed: {}", mask(to), e.getClass().getSimpleName());
        }
    }

    // "abdullah@gmail.com" -> "ab***@gmail.com"
    static String mask(String email) {
        String e = email.trim();
        int at = e.indexOf('@');
        if (at <= 0) return "***";
        return e.substring(0, Math.min(2, at)) + "***" + e.substring(at);
    }
}
