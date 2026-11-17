package com.findora.service;

import com.findora.config.AppProperties;
import com.findora.model.MatchBreakdown;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Groq (OpenAI-compatible) calls: turns a structured match score into one friendly sentence. The
 * deterministic MatchingEngine decides *which* pairs match; this only explains why. Without
 * GROQ_API_KEY (or on any error) a template explanation is returned instead.
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);
    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";

    private final RestClient http;
    private final AppProperties.Groq cfg;

    public AiService(RestClient http, AppProperties props) {
        this.http = http;
        this.cfg = props.groq();
    }

    public record ExplainArgs(String lostTitle, String foundTitle, String category, String lostLocation,
                              String foundLocation, int score, MatchBreakdown breakdown) {}

    private static String fallback(ExplainArgs a) {
        return "Matched on category \"" + a.category() + "\" and nearby locations (" + a.lostLocation() + " / "
                + a.foundLocation() + ") with an overall confidence of " + a.score() + "%.";
    }

    public String explainMatch(ExplainArgs a) {
        if (cfg.apiKey() == null || cfg.apiKey().isBlank()) {
            log.debug("GROQ_API_KEY not set - using fallback explanation");
            return fallback(a);
        }
        MatchBreakdown b = a.breakdown();
        String prompt = "You help reunite people with lost belongings. Two reports were matched by a scoring\n"
                + "algorithm. Write ONE short, friendly sentence (max 30 words) explaining to a user why these two\n"
                + "reports likely refer to the same item. Be specific but concise, plain text only, no markdown.\n\n"
                + "Lost item report: \"" + a.lostTitle() + "\" last seen at " + a.lostLocation() + "\n"
                + "Found item report: \"" + a.foundTitle() + "\" found at " + a.foundLocation() + "\n"
                + "Category: " + a.category() + "\n"
                + "Match confidence: " + a.score() + "%\n"
                + "Score breakdown (out of max): category " + b.getCategory() + "/25, location " + b.getLocation()
                + "/35, description " + b.getDescription() + "/20, date " + b.getDate() + "/10, photo/color "
                + b.getImage() + "/10";
        String text = complete(prompt, 0.4, 80);
        return text == null || text.isBlank() ? fallback(a) : text.trim();
    }

    /** 0-1 likelihood that two descriptions are the same object, or null if unavailable. */
    public Double semanticSimilarity(String a, String b) {
        if (cfg.apiKey() == null || cfg.apiKey().isBlank()
                || ((a == null || a.isBlank()) && (b == null || b.isBlank()))) {
            return null;
        }
        String prompt = "On a scale of 0 to 100, how likely do these two lost/found item descriptions refer\n"
                + "to the exact same physical object? Reply with ONLY the number, nothing else.\n\n"
                + "Description A: \"" + (a == null || a.isBlank() ? "(none)" : a) + "\"\n"
                + "Description B: \"" + (b == null || b.isBlank() ? "(none)" : b) + "\"";
        String text = complete(prompt, 0, 5);
        if (text == null) return null;
        String digits = text.replaceAll("[^0-9]", "");
        if (digits.isEmpty() || digits.length() > 9) return null;
        return Math.max(0, Math.min(100, Integer.parseInt(digits))) / 100.0;
    }

    @SuppressWarnings("unchecked")
    private String complete(String prompt, double temperature, int maxTokens) {
        try {
            Map<String, Object> body = Map.of(
                    "model", cfg.model(),
                    "messages", List.of(Map.of("role", "user", "content", prompt)),
                    "temperature", temperature,
                    "max_tokens", maxTokens);
            Map<String, Object> res = http.post().uri(GROQ_API_URL)
                    .header("Authorization", "Bearer " + cfg.apiKey())
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            if (res == null) return null;
            List<Map<String, Object>> choices = (List<Map<String, Object>>) res.get("choices");
            if (choices == null || choices.isEmpty()) return null;
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            return message == null ? null : (String) message.get("content");
        } catch (Exception e) {
            log.warn("Groq request failed", e);
            return null;
        }
    }
}
