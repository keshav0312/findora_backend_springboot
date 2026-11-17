package com.findora.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed view of the `findora.*` block in application.yml (fed from the same env vars as the Node app). */
@ConfigurationProperties(prefix = "findora")
public record AppProperties(
        Jwt jwt,
        List<String> corsOrigins,
        String frontendUrl,
        double proximityAlertKm,
        String uploadDir,
        boolean seed,
        Socket socket,
        Groq groq,
        Brevo brevo,
        WhatsApp whatsapp) {

    public record Jwt(String secret, String refreshSecret) {}

    public record Socket(String host, int port) {}

    public record Groq(String apiKey, String model) {}

    public record Brevo(String apiKey, String senderEmail, String senderName) {}

    public record WhatsApp(String token, String phoneNumberId, String apiVersion, String defaultCountryCode) {}
}
