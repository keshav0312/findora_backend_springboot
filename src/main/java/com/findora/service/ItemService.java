package com.findora.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.findora.config.AppProperties;
import com.findora.matching.GeoUtils;
import com.findora.matching.MatchResult;
import com.findora.matching.MatchingEngine;
import com.findora.model.Coordinates;
import com.findora.model.Item;
import com.findora.model.Match;
import com.findora.model.User;
import com.findora.model.VerificationQuestion;
import com.findora.repository.MatchRepository;
import com.findora.repository.UserRepository;
import com.findora.security.AuthUser;
import com.findora.web.ApiException;
import com.findora.web.ApiMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Lost/found report CRUD plus the cross-matching pipeline that runs whenever a report is filed. */
@Service
public class ItemService {

    private static final Logger log = LoggerFactory.getLogger(ItemService.class);
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");
    private static final int MAX_CANDIDATES = 200;

    private final MongoTemplate mongo;
    private final UserRepository users;
    private final MatchRepository matches;
    private final NotificationService notifications;
    private final RealtimeService realtime;
    private final AiService ai;
    private final EmailService email;
    private final WhatsAppService whatsapp;
    private final FileStorageService storage;
    private final ObjectMapper mapper;
    private final AppProperties props;

    public ItemService(MongoTemplate mongo, UserRepository users, MatchRepository matches,
                       NotificationService notifications, RealtimeService realtime, AiService ai,
                       EmailService email, WhatsAppService whatsapp, FileStorageService storage,
                       ObjectMapper mapper, AppProperties props) {
        this.mongo = mongo;
        this.users = users;
        this.matches = matches;
        this.notifications = notifications;
        this.realtime = realtime;
        this.ai = ai;
        this.email = email;
        this.whatsapp = whatsapp;
        this.storage = storage;
        this.mapper = mapper;
        this.props = props;
    }

    public record CreateResult(Item item, int matches) {}

    // ------------------------------------------------------------------ create

    public CreateResult create(ItemKind kind, AuthUser user, Map<String, String> body, List<MultipartFile> files) {
        String title = trimmed(body.get("title"));
        String category = trimmed(body.get("category"));
        String location = trimmed(body.get("location"));
        Instant date = parseDate(body.get("date"));
        if (title.isEmpty()) throw ApiException.badRequest("Title is required");
        if (!Item.CATEGORIES.contains(category)) throw ApiException.badRequest("Invalid category");
        if (location.isEmpty()) throw ApiException.badRequest("Location is required");
        if (date == null) throw ApiException.badRequest("A valid date is required");

        List<MultipartFile> uploads = files == null ? List.of()
                : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (uploads.size() > 5) throw ApiException.badRequest("A maximum of 5 photos is allowed");
        List<String> photos = new ArrayList<>();
        for (MultipartFile f : uploads) photos.add(storage.storeImage(f));

        Item item = kind.newItem();
        item.setTitle(title);
        item.setCategory(category);
        item.setDescription(orEmpty(body.get("description")));
        item.setColor(orEmpty(body.get("color")));
        item.setBrand(orEmpty(body.get("brand")));
        item.setLocation(location);
        item.setCity(trimmed(body.get("city")));
        item.setContactPhone(trimmed(body.get("contactPhone")));
        item.setCoordinates(new Coordinates(parseDouble(body.get("lat")), parseDouble(body.get("lng"))));
        item.setDate(date);
        Double reward = parseDouble(body.get("reward"));
        item.setReward(reward == null ? 0.0 : reward);
        item.setPhotos(photos);
        item.setVerificationQuestions(parseQuestions(body.get("verificationQuestions")));
        item.setOwner(user.id());
        item = mongo.save(item);

        realtime.toRoom("admins", "admin:activity", activity("report-" + item.getId(), "report",
                (kind == ItemKind.LOST ? "Lost" : "Found") + " report filed: \"" + item.getTitle() + "\"",
                item.getCity()));

        int created = crossMatch(kind, item, user);
        return new CreateResult(item, created);
    }

    /** Scores the new report against open reports of the opposite kind and notifies both parties. */
    private int crossMatch(ItemKind kind, Item item, AuthUser creator) {
        List<? extends Item> candidates = mongo.find(
                Query.query(Criteria.where("status").is("open")).limit(MAX_CANDIDATES), kind.opposite().type());

        int count = 0;
        for (Item candidate : candidates) {
            MatchResult result = MatchingEngine.compute(item, candidate);
            if (result.score() < MatchingEngine.MATCH_THRESHOLD) continue;

            boolean newIsLost = kind == ItemKind.LOST;
            Item lost = newIsLost ? item : candidate;
            Item found = newIsLost ? candidate : item;

            // Real-world distance when both reports have map coordinates; drives the WhatsApp alert.
            Double distanceKm = null;
            if (hasCoords(item) && hasCoords(candidate)) {
                distanceKm = GeoUtils.haversineKm(item.getCoordinates().getLat(), item.getCoordinates().getLng(),
                        candidate.getCoordinates().getLat(), candidate.getCoordinates().getLng());
            }
            boolean isNearby = distanceKm != null && distanceKm <= props.proximityAlertKm();

            String explanation = ai.explainMatch(new AiService.ExplainArgs(lost.getTitle(), found.getTitle(),
                    item.getCategory(), lost.getLocation(), found.getLocation(), result.score(), result.breakdown()));

            Match match = matches.findByLostItemAndFoundItem(lost.getId(), found.getId()).orElseGet(Match::new);
            match.setLostItem(lost.getId());
            match.setFoundItem(found.getId());
            match.setLostOwner(lost.getOwner());
            match.setFoundOwner(found.getOwner());
            match.setScore(result.score());
            match.setBreakdown(result.breakdown());
            match.setStatus("suggested");
            match.setAiExplanation(explanation);
            match.setDistanceKm(distanceKm);
            match = matches.save(match);
            count++;

            realtime.toRoom("admins", "admin:activity", activity("match-" + match.getId(), "match",
                    result.score() + "% match found: \"" + lost.getTitle() + "\" ↔ \"" + found.getTitle() + "\""
                            + (isNearby ? " (" + distanceKm + " km apart)" : ""),
                    item.getCity()));

            Map<String, String> phoneFor = new HashMap<>();
            phoneFor.put(found.getOwner(), found.getContactPhone());
            phoneFor.put(lost.getOwner(), lost.getContactPhone());

            for (String uid : new java.util.LinkedHashSet<>(List.of(lost.getOwner(), found.getOwner()))) {
                if (uid.equals(creator.id())) continue;

                notifications.createAndPush(uid, "match",
                        isNearby ? "Match found nearby! 📍" : "Possible match found",
                        "A " + result.score() + "% match was found for \"" + item.getTitle() + "\""
                                + (isNearby ? " — only " + distanceKm + " km away!" : "")
                                + " — " + explanation,
                        "/matches");

                User matched = users.findById(uid).orElse(null);
                if (matched == null) continue;
                email.sendMatchNotificationEmail(matched.getEmail(), matched.getName(), item.getTitle(),
                        result.score(), explanation, distanceKm);

                // WhatsApp is reserved for close-by matches (highest urgency).
                String phone = phoneFor.get(uid);
                if (phone == null || phone.isBlank()) phone = matched.getPhone();
                if (isNearby && phone != null && !phone.isBlank()) {
                    whatsapp.sendMatchWhatsApp(phone, item.getTitle(), result.score(), distanceKm,
                            props.frontendUrl() + "/matches");
                }
            }
        }
        return count;
    }

    private static boolean hasCoords(Item i) {
        Coordinates c = i.getCoordinates();
        return c != null && c.getLat() != null && c.getLng() != null;
    }

    private static Map<String, Object> activity(String id, String kind, String message, String city) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("kind", kind);
        m.put("message", message);
        m.put("city", city);
        m.put("at", Instant.now().toString());
        return m;
    }

    // -------------------------------------------------------------------- read

    public Map<String, Object> list(ItemKind kind, Map<String, String> params) {
        int page = parseIntOr(params.get("page"), 1);
        int limit = parseIntOr(params.get("limit"), 20);
        page = Math.max(1, page);
        limit = Math.min(50, Math.max(1, limit));

        List<Criteria> filters = new ArrayList<>();
        addEq(filters, "category", params.get("category"));
        addEq(filters, "status", params.get("status"));
        addContains(filters, "city", params.get("city"));
        addContains(filters, "color", params.get("color"));
        addContains(filters, "brand", params.get("brand"));
        String q = params.get("q");
        TextCriteria text = null;
        if (q != null && !q.isBlank()) text = TextCriteria.forDefaultLanguage().matchingAny(q.trim().split("\\s+"));

        Query count = buildQuery(filters, text);
        Query find = buildQuery(filters, text).with(NEWEST_FIRST).skip((long) (page - 1) * limit).limit(limit);

        List<? extends Item> items = mongo.find(find, kind.type());
        long total = mongo.count(count, kind.type());
        Map<String, User> owners = ownersById(items.stream().map(Item::getOwner).toList());

        List<Map<String, Object>> data = new ArrayList<>();
        for (Item i : items) data.add(ApiMapper.item(i, ApiMapper.publicOwner(owners.get(i.getOwner())), false));

        Map<String, Object> pagination = new LinkedHashMap<>();
        pagination.put("page", page);
        pagination.put("limit", limit);
        pagination.put("total", total);
        pagination.put("pages", (long) Math.ceil((double) total / limit));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("data", data);
        out.put("pagination", pagination);
        return out;
    }

    public List<Map<String, Object>> mine(ItemKind kind, AuthUser user) {
        List<? extends Item> items = mongo.find(
                Query.query(Criteria.where("owner").is(user.id())).with(NEWEST_FIRST), kind.type());
        return items.stream().map(i -> ApiMapper.item(i, i.getOwner(), true)).toList();
    }

    /** Verification answers are only revealed to the report owner (or staff). */
    public Map<String, Object> getOne(ItemKind kind, String id, AuthUser viewer) {
        Item item = find(kind, id);
        boolean isOwner = viewer != null && viewer.id().equals(item.getOwner());
        User owner = users.findById(item.getOwner()).orElse(null);
        return ApiMapper.item(item, ApiMapper.publicOwner(owner), isOwner);
    }

    // ------------------------------------------------------------------ mutate

    public Map<String, Object> update(ItemKind kind, String id, AuthUser user, Map<String, Object> body) {
        Item item = findOwned(kind, id, user);
        applyUpdates(item, body == null ? Map.of() : body);
        item = mongo.save(item);
        return ApiMapper.item(item, item.getOwner(), true);
    }

    public void remove(ItemKind kind, String id, AuthUser user) {
        Item item = findOwned(kind, id, user);
        mongo.remove(item);
    }

    public Map<String, Object> markResolved(ItemKind kind, String id, AuthUser user) {
        Item item = findOwned(kind, id, user);
        item.setStatus("closed");
        item = mongo.save(item);
        return ApiMapper.item(item, item.getOwner(), true);
    }

    // ----------------------------------------------------------------- helpers

    private Item find(ItemKind kind, String id) {
        Item item = Validation.validObjectId(id) ? mongo.findById(id, kind.type()) : null;
        if (item == null) throw ApiException.notFound(kind.label() + " report not found");
        return item;
    }

    private Item findOwned(ItemKind kind, String id, AuthUser user) {
        Item item = find(kind, id);
        if (!user.id().equals(item.getOwner()) && "user".equals(user.role())) {
            throw ApiException.forbidden("Not your report");
        }
        return item;
    }

    /**
     * Only these fields can be edited. (The Node version did Object.assign(item, req.body), which let a
     * caller overwrite `owner`, `photos`, timestamps, etc.)
     */
    private void applyUpdates(Item item, Map<String, Object> b) {
        String v;
        if ((v = str(b, "title")) != null) {
            if (v.isBlank()) throw ApiException.badRequest("Title is required");
            item.setTitle(v.trim());
        }
        if ((v = str(b, "category")) != null) {
            if (!Item.CATEGORIES.contains(v)) throw ApiException.badRequest("Invalid category");
            item.setCategory(v);
        }
        if ((v = str(b, "location")) != null) {
            if (v.isBlank()) throw ApiException.badRequest("Location is required");
            item.setLocation(v.trim());
        }
        if ((v = str(b, "status")) != null) {
            if (!Item.STATUSES.contains(v)) throw ApiException.badRequest("Invalid status");
            item.setStatus(v);
        }
        if ((v = str(b, "description")) != null) item.setDescription(v);
        if ((v = str(b, "color")) != null) item.setColor(v);
        if ((v = str(b, "brand")) != null) item.setBrand(v);
        if ((v = str(b, "city")) != null) item.setCity(v.trim());
        if ((v = str(b, "contactPhone")) != null) item.setContactPhone(v.trim());
        if (b.get("date") != null) {
            Instant d = parseDate(String.valueOf(b.get("date")));
            if (d == null) throw ApiException.badRequest("A valid date is required");
            item.setDate(d);
        }
        if (b.get("reward") != null) {
            Double r = parseDouble(String.valueOf(b.get("reward")));
            if (r == null || r < 0) throw ApiException.badRequest("Reward must be a positive number");
            item.setReward(r);
        }
        if (b.get("coordinates") instanceof Map<?, ?> c) {
            item.setCoordinates(new Coordinates(
                    parseDouble(c.get("lat") == null ? null : String.valueOf(c.get("lat"))),
                    parseDouble(c.get("lng") == null ? null : String.valueOf(c.get("lng")))));
        }
        if (b.get("verificationQuestions") != null) {
            item.setVerificationQuestions(mapper.convertValue(b.get("verificationQuestions"),
                    new TypeReference<List<VerificationQuestion>>() {}));
        }
    }

    private Map<String, User> ownersById(Collection<String> ids) {
        Set<String> unique = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, User> out = new HashMap<>();
        users.findAllById(unique).forEach(u -> out.put(u.getId(), u));
        return out;
    }

    private static Query buildQuery(List<Criteria> filters, TextCriteria text) {
        Query q = new Query();
        for (Criteria c : filters) q.addCriteria(c);
        if (text != null) q.addCriteria(text);
        return q;
    }

    private static void addEq(List<Criteria> filters, String field, String value) {
        if (value != null && !value.isBlank()) filters.add(Criteria.where(field).is(value));
    }

    /** Case-insensitive "contains" (the Node version compiled the raw query string as a regex). */
    private static void addContains(List<Criteria> filters, String field, String value) {
        if (value == null || value.isBlank()) return;
        filters.add(Criteria.where(field).regex(Pattern.compile(Pattern.quote(value.trim()), Pattern.CASE_INSENSITIVE)));
    }

    private List<VerificationQuestion> parseQuestions(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return mapper.readValue(json, new TypeReference<List<VerificationQuestion>>() {});
        } catch (Exception e) {
            log.debug("Ignoring malformed verificationQuestions");
            return new ArrayList<>();
        }
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static String trimmed(String s) {
        return s == null ? "" : s.trim();
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    private static Double parseDouble(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            double d = Double.parseDouble(s.trim());
            return Double.isNaN(d) || Double.isInfinite(d) ? null : d;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int parseIntOr(String s, int fallback) {
        try {
            return s == null ? fallback : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Accepts ISO instants, offset/local date-times and plain yyyy-MM-dd (treated as UTC midnight, like JS). */
    static Instant parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        String v = s.trim();
        try { return Instant.parse(v); } catch (DateTimeParseException ignored) { }
        try { return OffsetDateTime.parse(v).toInstant(); } catch (DateTimeParseException ignored) { }
        try { return LocalDateTime.parse(v).toInstant(ZoneOffset.UTC); } catch (DateTimeParseException ignored) { }
        try { return LocalDate.parse(v).atStartOfDay().toInstant(ZoneOffset.UTC); } catch (DateTimeParseException ignored) { }
        return null;
    }
}
