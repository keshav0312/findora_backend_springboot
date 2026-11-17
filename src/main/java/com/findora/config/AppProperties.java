package com.findora.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Typed view of the settings declared in application.yml. */
@Component
public class AppProperties {

    public record Jwt(String secret, String refreshSecret) {}
    public record Socket(String host, int port) {}
    public record Groq(String apiKey, String model) {}
    public record Brevo(String apiKey, String senderEmail, String senderName) {}
    public record WhatsApp(String token, String phoneNumberId, String apiVersion, String defaultCountryCode) {}
    public record Cloudinary(String cloudName, String apiKey, String apiSecret) {}

    private final Jwt jwt;
    private final List<String> corsOrigins;
    private final String frontendUrl;
    private final double proximityAlertKm;
    private final String uploadDir;
    private final boolean seed;
    private final Socket socket;
    private final Groq groq;
    private final Brevo brevo;
    private final WhatsApp whatsapp;
    private final Cloudinary cloudinary;

    public AppProperties(
            @Value("${findora.jwt.secret}") String jwtSecret,
            @Value("${findora.jwt.refresh-secret}") String jwtRefreshSecret,
            @Value("${findora.cors-origins}") List<String> corsOrigins,
            @Value("${findora.frontend-url}") String frontendUrl,
            @Value("${findora.proximity-alert-km}") double proximityAlertKm,
            @Value("${findora.upload-dir}") String uploadDir,
            @Value("${findora.seed}") boolean seed,
            @Value("${findora.socket.host}") String socketHost,
            @Value("${findora.socket.port}") int socketPort,
            @Value("${findora.groq.api-key}") String groqApiKey,
            @Value("${findora.groq.model}") String groqModel,
            @Value("${findora.brevo.api-key}") String brevoApiKey,
            @Value("${findora.brevo.sender-email}") String brevoSenderEmail,
            @Value("${findora.brevo.sender-name}") String brevoSenderName,
            @Value("${findora.whatsapp.token}") String whatsappToken,
            @Value("${findora.whatsapp.phone-number-id}") String whatsappPhoneNumberId,
            @Value("${findora.whatsapp.api-version}") String whatsappApiVersion,
            @Value("${findora.whatsapp.default-country-code}") String whatsappCountryCode,
            @Value("${findora.cloudinary.cloud-name}") String cloudinaryCloudName,
            @Value("${findora.cloudinary.api-key}") String cloudinaryApiKey,
            @Value("${findora.cloudinary.api-secret}") String cloudinaryApiSecret) {
        this.jwt = new Jwt(jwtSecret, jwtRefreshSecret);
        this.corsOrigins = List.copyOf(corsOrigins);
        this.frontendUrl = frontendUrl;
        this.proximityAlertKm = proximityAlertKm;
        this.uploadDir = uploadDir;
        this.seed = seed;
        this.socket = new Socket(socketHost, socketPort);
        this.groq = new Groq(groqApiKey, groqModel);
        this.brevo = new Brevo(brevoApiKey, brevoSenderEmail, brevoSenderName);
        this.whatsapp = new WhatsApp(
                whatsappToken, whatsappPhoneNumberId, whatsappApiVersion, whatsappCountryCode);
        this.cloudinary = new Cloudinary(cloudinaryCloudName, cloudinaryApiKey, cloudinaryApiSecret);
    }

    public Jwt jwt() {
        return jwt;
    }

    public List<String> corsOrigins() {
        return corsOrigins;
    }

    public String frontendUrl() {
        return frontendUrl;
    }

    public double proximityAlertKm() {
        return proximityAlertKm;
    }

    public String uploadDir() {
        return uploadDir;
    }

    public boolean seed() {
        return seed;
    }

    public Socket socket() {
        return socket;
    }

    public Groq groq() {
        return groq;
    }

    public Brevo brevo() {
        return brevo;
    }

    public WhatsApp whatsapp() {
        return whatsapp;
    }

    public Cloudinary cloudinary() {
        return cloudinary;
    }
}
