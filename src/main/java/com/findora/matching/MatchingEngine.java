package com.findora.matching;

import com.findora.model.MatchBreakdown;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Findora matching engine - a straight port of utils/matching.ts.
 *
 * Deterministic, explainable 0-100 score: category 25%, location 35%, description 20%, date 10%,
 * photo/colour signal 10%. Swap in embeddings later without changing the {@link MatchResult} shape.
 */
public final class MatchingEngine {

    /** Reports below this score are not surfaced as a "possible match". */
    public static final int MATCH_THRESHOLD = 45;

    private static final int W_CATEGORY = 25;
    private static final int W_LOCATION = 35;
    private static final int W_DESCRIPTION = 20;
    private static final int W_DATE = 10;
    private static final int W_IMAGE = 10;

    private MatchingEngine() {}

    private static String normalize(String s) {
        return s == null ? "" : s.toLowerCase().trim();
    }

    private static Set<String> tokenize(String s) {
        Set<String> tokens = new HashSet<>();
        for (String t : normalize(s).split("[^a-z0-9]+")) {
            if (t.length() > 2) tokens.add(t);
        }
        return tokens;
    }

    /** Jaccard similarity over token sets - cheap stand-in for text embeddings. */
    static double textSimilarity(String a, String b) {
        Set<String> ta = tokenize(a);
        Set<String> tb = tokenize(b);
        if (ta.isEmpty() || tb.isEmpty()) return 0;
        int intersection = 0;
        for (String t : ta) if (tb.contains(t)) intersection++;
        Set<String> union = new HashSet<>(ta);
        union.addAll(tb);
        return union.isEmpty() ? 0 : (double) intersection / union.size();
    }

    static double locationSimilarity(Matchable a, Matchable b) {
        String cityA = normalize(a.getCity());
        String cityB = normalize(b.getCity());
        String locA = normalize(a.getLocation());
        String locB = normalize(b.getLocation());

        if (!locA.isEmpty() && !locB.isEmpty() && locA.equals(locB)) return 1;
        if (!cityA.isEmpty() && !cityB.isEmpty() && cityA.equals(cityB)) {
            double overlap = textSimilarity(locA, locB);
            return 0.6 + overlap * 0.4;
        }
        return textSimilarity(locA, locB) * 0.5;
    }

    static double dateSimilarity(Instant a, Instant b) {
        if (a == null || b == null) return 0;
        double diffDays = Math.abs(Duration.between(a, b).toMillis()) / (1000.0 * 60 * 60 * 24);
        if (diffDays <= 1) return 1;
        if (diffDays <= 3) return 0.8;
        if (diffDays <= 7) return 0.5;
        if (diffDays <= 14) return 0.25;
        return 0;
    }

    static double imageSimilarity(Matchable a, Matchable b) {
        boolean bothHavePhotos = hasPhotos(a.getPhotos()) && hasPhotos(b.getPhotos());
        String colorA = normalize(a.getColor());
        int colorMatch = !colorA.isEmpty() && colorA.equals(normalize(b.getColor())) ? 1 : 0;
        if (!bothHavePhotos && colorMatch == 0) return 0;
        return bothHavePhotos ? 0.6 + colorMatch * 0.4 : colorMatch * 0.5;
    }

    private static boolean hasPhotos(List<String> photos) {
        return photos != null && !photos.isEmpty();
    }

    /** JS Math.round semantics (round half up). */
    private static int round(double v) {
        return (int) Math.floor(v + 0.5);
    }

    public static MatchResult compute(Matchable a, Matchable b) {
        double categoryScore = normalize(a.getCategory()).equals(normalize(b.getCategory())) ? 1 : 0;

        double description = textSimilarity(
                (a.getDescription() == null ? "" : a.getDescription()) + " " + (a.getBrand() == null ? "" : a.getBrand()),
                (b.getDescription() == null ? "" : b.getDescription()) + " " + (b.getBrand() == null ? "" : b.getBrand()));

        double location = locationSimilarity(a, b);
        double date = dateSimilarity(a.getDate(), b.getDate());
        double image = imageSimilarity(a, b);

        MatchBreakdown breakdown = new MatchBreakdown(
                round(categoryScore * W_CATEGORY),
                round(location * W_LOCATION),
                round(description * W_DESCRIPTION),
                round(date * W_DATE),
                round(image * W_IMAGE));

        int score = breakdown.getCategory() + breakdown.getLocation() + breakdown.getDescription()
                + breakdown.getDate() + breakdown.getImage();
        return new MatchResult(score, breakdown);
    }
}
