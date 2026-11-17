package com.findora.service;

import com.findora.config.AppProperties;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;

/**
 * Transactional email via Brevo's HTTPS API. With no BREVO_API_KEY every method just logs, so local
 * development works without a provider. All public methods are fire-and-forget (@Async).
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";

    private final RestClient http;
    private final AppProperties.Brevo cfg;
    private final String frontendUrl;

    public EmailService(RestClient http, AppProperties props) {
        this.http = http;
        this.cfg = props.brevo();
        this.frontendUrl = props.frontendUrl();
    }

    private boolean send(String toEmail, String toName, String subject, String html) {
        if (cfg.apiKey() == null || cfg.apiKey().isBlank()) {
            log.info("[email:disabled] Email provider is not configured");
            return false;
        }
        try {
            Map<String, Object> body = Map.of(
                    "sender", Map.of("name", cfg.senderName(), "email", cfg.senderEmail()),
                    "to", List.of(Map.of("email", toEmail, "name", toName == null ? "" : toName)),
                    "subject", subject,
                    "htmlContent", html);
            http.post().uri(BREVO_API_URL)
                    .header("api-key", cfg.apiKey())
                    .header("accept", "application/json")
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.error("[email:error] Brevo request failed", e);
            return false;
        }
    }

    private static String esc(String s) {
        return HtmlUtils.htmlEscape(s == null ? "" : s);
    }

    /** `title` and `bodyHtml` must already be safe HTML (callers escape user-supplied values). */
    private static String layout(String title, String bodyHtml, String ctaLabel, String ctaUrl) {
        String cta = ctaLabel != null && ctaUrl != null
                ? "<a href=\"" + ctaUrl + "\" style=\"display:inline-block;margin-top:24px;background:#4338ca;color:#fff;"
                + "text-decoration:none;padding:12px 24px;border-radius:8px;font-size:14px;font-weight:600;\">"
                + ctaLabel + "</a>"
                : "";
        return "<div style=\"font-family:Inter,Arial,sans-serif;background:#f4f5ff;padding:32px;\">"
                + "<div style=\"max-width:520px;margin:0 auto;background:#ffffff;border-radius:16px;overflow:hidden;"
                + "border:1px solid #e5e7eb;\">"
                + "<div style=\"background:#4338ca;padding:24px 32px;\">"
                + "<span style=\"color:#ffffff;font-size:20px;font-weight:700;\">📍 Findora</span></div>"
                + "<div style=\"padding:32px;color:#1f2937;\">"
                + "<h2 style=\"margin:0 0 16px;font-size:18px;\">" + title + "</h2>"
                + "<div style=\"font-size:14px;line-height:1.6;color:#374151;\">" + bodyHtml + "</div>"
                + cta + "</div>"
                + "<div style=\"padding:16px 32px;background:#f9fafb;color:#9ca3af;font-size:12px;\">"
                + "Findora — Lost Something? Findora Helps You Find It.</div></div></div>";
    }

    @Async
    public void sendVerificationEmail(String to, String name, String token) {
        String url = frontendUrl + "/verify-email?token=" + token;
        send(to, name, "Verify your Findora account", layout(
                "Welcome, " + esc(name) + " 👋",
                "Thanks for joining Findora. Please confirm your email address to activate your account and start "
                        + "reporting or searching for lost &amp; found items.",
                "Verify my email", url));
    }

    @Async
    public void sendPasswordResetEmail(String to, String name, String token) {
        String url = frontendUrl + "/forgot-password?token=" + token;
        send(to, name, "Reset your Findora password", layout(
                "Reset your password",
                "We received a request to reset your Findora password. This link expires in 30 minutes. "
                        + "If you didn't request this, you can safely ignore this email.",
                "Reset password", url));
    }

    @Async
    public void sendMatchNotificationEmail(String to, String name, String itemTitle, int score,
                                           String aiExplanation, Double distanceKm) {
        String proximity = distanceKm != null
                ? "<br/>📍 <b>" + distanceKm + " km</b> away from where it was lost/found — very close by!" : "";
        String explanation = aiExplanation != null && !aiExplanation.isBlank()
                ? "<br/><br/><i>" + esc(aiExplanation) + "</i>" : "";
        send(to, name, "We found a possible match for \"" + itemTitle + "\" (" + score + "%)", layout(
                "We found a possible match! 🎉",
                "Our AI matching engine found a <b>" + score + "% match</b> for <b>" + esc(itemTitle) + "</b>."
                        + proximity + explanation
                        + "<br/><br/>Open Findora to review the details and start a chat with the other party.",
                "View match", frontendUrl + "/matches"));
    }

    @Async
    public void sendItemReturnedEmail(String to, String name, String itemTitle) {
        send(to, name, "\"" + itemTitle + "\" was marked as returned", layout(
                "Case closed 🎉",
                "Great news — <b>" + esc(itemTitle) + "</b> has been marked as returned and closed. Thank you for "
                        + "using Findora to help reunite people with their belongings.",
                null, null));
    }
}
