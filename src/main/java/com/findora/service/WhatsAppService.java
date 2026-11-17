package com.findora.service;

import com.findora.config.AppProperties;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * WhatsApp Cloud API (Meta) proximity alerts. Without WHATSAPP_TOKEN / WHATSAPP_PHONE_NUMBER_ID it only
 * logs. Fire-and-forget (@Async).
 */
@Service
public class WhatsAppService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppService.class);

    private final RestClient http;
    private final AppProperties.WhatsApp cfg;

    public WhatsAppService(RestClient http, AppProperties props) {
        this.http = http;
        this.cfg = props.whatsapp();
    }

    /** Digits-only E.164 without "+"; 10-digit numbers get the default country code (India = 91). */
    public String normalizePhone(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return null;
        if (digits.length() == 10) return cfg.defaultCountryCode() + digits;
        return digits;
    }

    private boolean sendText(String toRaw, String body) {
        boolean configured = cfg.token() != null && !cfg.token().isBlank()
                && cfg.phoneNumberId() != null && !cfg.phoneNumberId().isBlank();
        if (!configured) {
            log.info("[whatsapp:disabled] WhatsApp provider is not configured");
            return false;
        }
        String to = normalizePhone(toRaw);
        if (to == null) {
            log.info("[whatsapp:skip] No valid phone number to message");
            return false;
        }
        try {
            String url = "https://graph.facebook.com/" + cfg.apiVersion() + "/" + cfg.phoneNumberId() + "/messages";
            http.post().uri(url)
                    .header("Authorization", "Bearer " + cfg.token())
                    .body(Map.of(
                            "messaging_product", "whatsapp",
                            "to", to,
                            "type", "text",
                            "text", Map.of("body", body, "preview_url", true)))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.error("[whatsapp:error] Graph API request failed", e);
            return false;
        }
    }

    @Async
    public void sendMatchWhatsApp(String toPhone, String itemTitle, int score, Double distanceKm, String matchUrl) {
        String distanceLine = distanceKm != null
                ? "📍 Only *" + distanceKm + " km* away from where it was lost/found!\n" : "";
        String body = "🎉 *Findora Match Alert*\n\n"
                + "We found a *" + score + "% match* for \"*" + itemTitle + "*\".\n"
                + distanceLine
                + "\nOpen Findora to view details and chat: " + matchUrl;
        sendText(toPhone, body);
    }
}
