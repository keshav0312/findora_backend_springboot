package com.findora.service;

import com.findora.model.FoundItem;
import com.findora.model.Item;
import com.findora.model.LostItem;
import com.findora.model.Match;
import com.findora.model.User;
import com.findora.repository.UserRepository;
import com.findora.web.ApiException;
import com.findora.web.ApiMapper;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AdminService {

    private static final String[] MONTHS = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    private final MongoTemplate mongo;
    private final UserRepository users;
    private final PasswordEncoder encoder;

    public AdminService(MongoTemplate mongo, UserRepository users, PasswordEncoder encoder) {
        this.mongo = mongo;
        this.users = users;
        this.encoder = encoder;
    }

    public Map<String, Object> analytics() {
        long totalUsers = mongo.count(new Query(), User.class);
        long lostCount = mongo.count(new Query(), LostItem.class);
        long foundCount = mongo.count(new Query(), FoundItem.class);
        Query closed = Query.query(Criteria.where("status").is("closed"));
        long resolved = mongo.count(closed, LostItem.class) + mongo.count(closed, FoundItem.class);
        long matches = mongo.count(new Query(), Match.class);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalUsers", totalUsers);
        data.put("lostCount", lostCount);
        data.put("foundCount", foundCount);
        data.put("resolvedCount", resolved);
        data.put("matches", matches);
        data.put("recoveryRate", lostCount + foundCount > 0
                ? (int) Math.floor((double) resolved / (lostCount + foundCount) * 100 + 0.5) : 0);
        data.put("topCategories", topBy("category", 6, "Other", "category"));
        data.put("topCities", topBy("city", 8, "Unknown", "city"));
        data.put("trend", trend());
        return data;
    }

    /** Count of lost reports grouped by a field, biggest first. */
    private List<Map<String, Object>> topBy(String field, int limit, String emptyLabel, String outKey) {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.group(field).count().as("count"),
                Aggregation.sort(Sort.Direction.DESC, "count"),
                Aggregation.limit(limit));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Document d : mongo.aggregate(agg, LostItem.class, Document.class).getMappedResults()) {
            Object id = d.get("_id");
            String label = id == null || id.toString().isEmpty() ? emptyLabel : id.toString();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put(outKey, label);
            row.put("count", ((Number) d.get("count")).intValue());
            out.add(row);
        }
        return out;
    }

    /** Combined lost+found report volume for the last 6 calendar months (UTC), oldest first. */
    private List<Map<String, Object>> trend() {
        YearMonth now = YearMonth.now(ZoneOffset.UTC);
        Instant since = now.minusMonths(5).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Map<YearMonth, Integer> counts = new HashMap<>();
        for (Class<? extends Item> type : List.of(LostItem.class, FoundItem.class)) {
            Query q = Query.query(Criteria.where("createdAt").gte(since));
            q.fields().include("createdAt");
            for (Item i : mongo.find(q, type)) {
                if (i.getCreatedAt() == null) continue;
                counts.merge(YearMonth.from(i.getCreatedAt().atZone(ZoneOffset.UTC)), 1, Integer::sum);
            }
        }
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth ym = now.minusMonths(i);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("label", MONTHS[ym.getMonthValue() - 1]);
            row.put("value", counts.getOrDefault(ym, 0));
            trend.add(row);
        }
        return trend;
    }

    public List<Map<String, Object>> listUsers() {
        return users.findTop200ByOrderByCreatedAtDesc().stream().map(ApiMapper::adminUser).toList();
    }

    public Map<String, Object> createStaff(String name, String rawEmail, String password, String role) {
        if (Validation.blank(name) || Validation.blank(rawEmail) || Validation.blank(password)) {
            throw ApiException.badRequest("Name, email and password are required");
        }
        if (password.length() < 6) throw ApiException.badRequest("Password must be at least 6 characters");
        if (!"admin".equals(role) && !"police".equals(role)) {
            throw ApiException.badRequest("Role must be 'admin' or 'police'");
        }
        String email = Validation.normalizeEmail(rawEmail);
        if (!Validation.validEmail(email)) throw ApiException.badRequest("Please provide a valid email address");
        if (users.findByEmail(email).isPresent()) {
            throw ApiException.conflict("An account with this email already exists");
        }

        User u = new User();
        u.setName(name.trim());
        u.setEmail(email);
        u.setPassword(encoder.encode(password));
        u.setRole(role);
        u.setVerified(true); // staff accounts are created pre-verified
        u = users.save(u);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("name", u.getName());
        m.put("email", u.getEmail());
        m.put("avatar", u.getAvatar());
        m.put("role", u.getRole());
        m.put("isVerified", true);
        m.put("trustPoints", u.getTrustPoints());
        m.put("badge", u.getBadge());
        m.put("createdAt", ApiMapper.iso(u.getCreatedAt()));
        return m;
    }

    /**
     * Same behaviour as the Node version: "restricting" a user flips isVerified to false. It does not block
     * login - add a real `banned` flag if you need to lock accounts out.
     */
    public Map<String, Object> banUser(String id) {
        User u = Validation.validObjectId(id) ? users.findById(id).orElse(null) : null;
        if (u == null) throw ApiException.notFound("User not found");
        u.setVerified(false);
        return ApiMapper.adminUser(users.save(u));
    }

    /** Latest 30 reports across lost + found, newest first. */
    public List<Map<String, Object>> recentReports() {
        Query q = new Query().with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(20);
        List<Item> lost = new ArrayList<>(mongo.find(q, LostItem.class));
        List<Item> found = new ArrayList<>(mongo.find(q, FoundItem.class));

        Set<String> ownerIds = new java.util.HashSet<>();
        lost.forEach(i -> ownerIds.add(i.getOwner()));
        found.forEach(i -> ownerIds.add(i.getOwner()));
        Map<String, User> owners = new HashMap<>();
        users.findAllById(ownerIds).forEach(u -> owners.put(u.getId(), u));

        record Row(Item item, String type) {}
        List<Row> rows = new ArrayList<>();
        lost.forEach(i -> rows.add(new Row(i, "lost")));
        found.forEach(i -> rows.add(new Row(i, "found")));
        rows.sort(Comparator.comparing((Row r) -> r.item().getCreatedAt() == null ? Instant.EPOCH : r.item().getCreatedAt())
                .reversed());

        return rows.stream().limit(30).map(r -> {
            Map<String, Object> m = new LinkedHashMap<>(
                    ApiMapper.item(r.item(), ApiMapper.nameOnlyOwner(owners.get(r.item().getOwner())), true));
            m.put("type", r.type());
            return m;
        }).collect(Collectors.toList());
    }
}
