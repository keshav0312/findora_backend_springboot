package com.findora.web;

import com.findora.model.Coordinates;
import com.findora.model.Item;
import com.findora.model.Match;
import com.findora.model.MatchBreakdown;
import com.findora.model.Message;
import com.findora.model.Notification;
import com.findora.model.User;
import com.findora.model.VerificationQuestion;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts entities to the exact JSON shapes the Express/Mongoose API produced (`_id`, ISO date strings,
 * populated sub-documents...). Timestamps are emitted as strings so the same maps can also be sent over
 * Socket.IO, whose serializer knows nothing about java.time.
 */
public final class ApiMapper {

    private ApiMapper() {}

    public static String iso(Instant t) {
        return t == null ? null : t.toString();
    }

    private static int n(Integer v) {
        return v == null ? 0 : v;
    }

    // ---------------------------------------------------------------- users

    /** Shape returned by /auth/* (the frontend's `User` type: `id`, not `_id`). */
    public static Map<String, Object> authUser(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("name", u.getName());
        m.put("email", u.getEmail());
        m.put("avatar", u.getAvatar());
        m.put("role", u.getRole());
        m.put("city", u.getCity());
        m.put("phone", u.getPhone());
        m.put("isVerified", Boolean.TRUE.equals(u.getVerified()));
        m.put("trustPoints", n(u.getTrustPoints()));
        m.put("badge", u.getBadge());
        m.put("createdAt", iso(u.getCreatedAt()));
        return m;
    }

    /** Admin user list: both `_id` and `id` (the admin page reads `id`, the old API only sent `_id`). */
    public static Map<String, Object> adminUser(User u) {
        Map<String, Object> m = authUser(u);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("_id", u.getId());
        out.putAll(m);
        out.put("lastSeen", iso(u.getLastSeen()));
        out.put("updatedAt", iso(u.getUpdatedAt()));
        return out;
    }

    /** Populated `owner` / `lostOwner` / `foundOwner`: name avatar trustPoints badge lastSeen. */
    public static Map<String, Object> publicOwner(User u) {
        if (u == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("_id", u.getId());
        m.put("name", u.getName());
        m.put("avatar", u.getAvatar());
        m.put("trustPoints", n(u.getTrustPoints()));
        m.put("badge", u.getBadge());
        m.put("lastSeen", iso(u.getLastSeen()));
        return m;
    }

    /** Populated owner with just name + avatar (saved items). */
    public static Map<String, Object> basicOwner(User u) {
        if (u == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("_id", u.getId());
        m.put("name", u.getName());
        m.put("avatar", u.getAvatar());
        return m;
    }

    /** Populated owner with only the name (admin report feed). */
    public static Map<String, Object> nameOnlyOwner(User u) {
        if (u == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("_id", u.getId());
        m.put("name", u.getName());
        return m;
    }

    public static Map<String, Object> leaderboardEntry(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("_id", u.getId());
        m.put("name", u.getName());
        m.put("avatar", u.getAvatar());
        m.put("city", u.getCity());
        m.put("trustPoints", n(u.getTrustPoints()));
        m.put("badge", u.getBadge());
        m.put("createdAt", iso(u.getCreatedAt()));
        return m;
    }

    // ---------------------------------------------------------------- items

    /**
     * @param owner       what to put in `owner`: the owner id string, or a populated owner map
     * @param showAnswers whether verification answers are included (report owner only)
     */
    public static Map<String, Object> item(Item i, Object owner, boolean showAnswers) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("_id", i.getId());
        m.put("title", i.getTitle());
        m.put("category", i.getCategory());
        m.put("description", i.getDescription());
        m.put("color", i.getColor());
        m.put("brand", i.getBrand());
        m.put("location", i.getLocation());
        m.put("city", i.getCity());
        m.put("contactPhone", i.getContactPhone());
        Coordinates c = i.getCoordinates() == null ? new Coordinates() : i.getCoordinates();
        Map<String, Object> coords = new LinkedHashMap<>();
        coords.put("lat", c.getLat());
        coords.put("lng", c.getLng());
        m.put("coordinates", coords);
        m.put("date", iso(i.getDate()));
        m.put("reward", i.getReward() == null ? 0 : i.getReward());
        m.put("photos", i.getPhotos() == null ? List.of() : i.getPhotos());
        List<Map<String, Object>> qs = new ArrayList<>();
        if (i.getVerificationQuestions() != null) {
            for (VerificationQuestion q : i.getVerificationQuestions()) {
                Map<String, Object> qm = new LinkedHashMap<>();
                qm.put("question", q.getQuestion());
                if (showAnswers) qm.put("answer", q.getAnswer());
                qs.add(qm);
            }
        }
        m.put("verificationQuestions", qs);
        m.put("status", i.getStatus());
        m.put("owner", owner);
        m.put("createdAt", iso(i.getCreatedAt()));
        m.put("updatedAt", iso(i.getUpdatedAt()));
        return m;
    }

    // -------------------------------------------------------------- matches

    public static Map<String, Object> breakdown(MatchBreakdown b) {
        MatchBreakdown x = b == null ? new MatchBreakdown() : b;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("category", x.getCategory());
        m.put("location", x.getLocation());
        m.put("description", x.getDescription());
        m.put("date", x.getDate());
        m.put("image", x.getImage());
        return m;
    }

    /** Unpopulated match (ids only) - what PATCH /matches/:id and the socket events carry. */
    public static Map<String, Object> match(Match x) {
        return match(x, x.getLostItem(), x.getFoundItem(), x.getLostOwner(), x.getFoundOwner());
    }

    public static Map<String, Object> match(Match x, Object lostItem, Object foundItem, Object lostOwner, Object foundOwner) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("_id", x.getId());
        m.put("lostItem", lostItem);
        m.put("foundItem", foundItem);
        m.put("lostOwner", lostOwner);
        m.put("foundOwner", foundOwner);
        m.put("score", x.getScore());
        m.put("breakdown", breakdown(x.getBreakdown()));
        m.put("status", x.getStatus());
        m.put("aiExplanation", x.getAiExplanation());
        m.put("distanceKm", x.getDistanceKm());
        m.put("createdAt", iso(x.getCreatedAt()));
        m.put("updatedAt", iso(x.getUpdatedAt()));
        return m;
    }

    // ------------------------------------------------- messages/notifications

    public static Map<String, Object> message(Message x) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("_id", x.getId());
        m.put("conversationId", x.getConversationId());
        m.put("sender", x.getSender());
        m.put("recipient", x.getRecipient());
        m.put("matchId", x.getMatchId());
        m.put("text", x.getText());
        if (x.getAttachment() != null) {
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("kind", x.getAttachment().getKind());
            a.put("url", x.getAttachment().getUrl());
            a.put("durationSeconds", x.getAttachment().getDurationSeconds());
            m.put("attachment", a);
        } else {
            m.put("attachment", null);
        }
        if (x.getLocation() != null) {
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("lat", x.getLocation().getLat());
            l.put("lng", x.getLocation().getLng());
            l.put("label", x.getLocation().getLabel());
            m.put("location", l);
        } else {
            m.put("location", null);
        }
        m.put("read", Boolean.TRUE.equals(x.getRead()));
        m.put("createdAt", iso(x.getCreatedAt()));
        m.put("updatedAt", iso(x.getUpdatedAt()));
        return m;
    }

    public static Map<String, Object> notification(Notification x) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("_id", x.getId());
        m.put("user", x.getUser());
        m.put("type", x.getType());
        m.put("title", x.getTitle());
        m.put("body", x.getBody());
        m.put("link", x.getLink());
        m.put("read", Boolean.TRUE.equals(x.getRead()));
        m.put("createdAt", iso(x.getCreatedAt()));
        m.put("updatedAt", iso(x.getUpdatedAt()));
        return m;
    }

    /** Ephemeral notification pushed over the socket for a chat message (not persisted under this id). */
    public static Map<String, Object> liveMessageNotification(Message msg, String body, String matchId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("_id", "live-" + msg.getId());
        m.put("type", "message");
        m.put("title", "New message");
        m.put("body", body);
        m.put("link", "/chat/" + matchId);
        m.put("read", false);
        m.put("createdAt", Instant.now().toString());
        return m;
    }
}
