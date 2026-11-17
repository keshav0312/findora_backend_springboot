package com.findora.service;

import com.findora.model.FoundItem;
import com.findora.model.Item;
import com.findora.model.LostItem;
import com.findora.model.Match;
import com.findora.model.User;
import com.findora.repository.MatchRepository;
import com.findora.repository.UserRepository;
import com.findora.security.AuthUser;
import com.findora.web.ApiException;
import com.findora.web.ApiMapper;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
public class MatchService {

    private final MatchRepository matches;
    private final UserRepository users;
    private final MongoTemplate mongo;
    private final NotificationService notifications;
    private final RealtimeService realtime;
    private final EmailService email;

    public MatchService(MatchRepository matches, UserRepository users, MongoTemplate mongo,
                        NotificationService notifications, RealtimeService realtime, EmailService email) {
        this.matches = matches;
        this.users = users;
        this.mongo = mongo;
        this.notifications = notifications;
        this.realtime = realtime;
        this.email = email;
    }

    public List<Map<String, Object>> myMatches(AuthUser user) {
        Sort sort = Sort.by(Sort.Direction.DESC, "score").and(Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Match> list = matches.findByLostOwnerOrFoundOwner(user.id(), user.id(), sort);

        Set<String> lostIds = new LinkedHashSet<>();
        Set<String> foundIds = new LinkedHashSet<>();
        Set<String> userIds = new LinkedHashSet<>();
        for (Match m : list) {
            lostIds.add(m.getLostItem());
            foundIds.add(m.getFoundItem());
            userIds.add(m.getLostOwner());
            userIds.add(m.getFoundOwner());
        }
        Map<String, Item> lost = byId(lostIds, LostItem.class);
        Map<String, Item> found = byId(foundIds, FoundItem.class);
        Map<String, User> owners = new HashMap<>();
        users.findAllById(userIds).forEach(u -> owners.put(u.getId(), u));

        return list.stream().map(m -> ApiMapper.match(m,
                populate(lost.get(m.getLostItem()), user),
                populate(found.get(m.getFoundItem()), user),
                ApiMapper.publicOwner(owners.get(m.getLostOwner())),
                ApiMapper.publicOwner(owners.get(m.getFoundOwner())))).toList();
    }

    /** Embedded item; the other party never sees the owner's verification answers. */
    private static Map<String, Object> populate(Item item, AuthUser viewer) {
        return item == null ? null : ApiMapper.item(item, item.getOwner(), viewer.id().equals(item.getOwner()));
    }

    private <T extends Item> Map<String, Item> byId(Set<String> ids, Class<T> type) {
        Map<String, Item> out = new HashMap<>();
        if (ids.isEmpty()) return out;
        mongo.find(Query.query(Criteria.where("id").in(ids)), type).forEach(i -> out.put(i.getId(), i));
        return out;
    }

    public Map<String, Object> updateStatus(String matchId, AuthUser user, String status) {
        Match match = Validation.validObjectId(matchId) ? matches.findById(matchId).orElse(null) : null;
        if (match == null) throw ApiException.notFound("Match not found");
        if (!user.id().equals(match.getLostOwner()) && !user.id().equals(match.getFoundOwner())) {
            throw ApiException.forbidden("Not part of this match");
        }
        if (status == null || !Match.STATUSES.contains(status)) throw ApiException.badRequest("Invalid status");

        String previous = match.getStatus();
        match.setStatus(status);
        match = matches.save(match);

        if ("returned".equals(status)) {
            // Only reward the first transition to "returned" (the Node version paid out on every call).
            if (!"returned".equals(previous)) onReturned(match);
        } else {
            Map<String, Object> payload = ApiMapper.match(match);
            realtime.toRoom(match.getLostOwner(), "match:updated", payload);
            realtime.toRoom(match.getFoundOwner(), "match:updated", payload);
        }
        return ApiMapper.match(match);
    }

    private void onReturned(Match match) {
        mongo.updateFirst(Query.query(Criteria.where("id").is(match.getLostOwner())),
                new Update().inc("trustPoints", 10), User.class);
        mongo.updateFirst(Query.query(Criteria.where("id").is(match.getFoundOwner())),
                new Update().inc("trustPoints", 15), User.class);

        LostItem lost = mongo.findById(match.getLostItem(), LostItem.class);
        String itemTitle = lost != null && lost.getTitle() != null ? lost.getTitle() : "your item";

        Map<String, Object> activity = new java.util.LinkedHashMap<>();
        activity.put("id", "returned-" + match.getId());
        activity.put("kind", "returned");
        activity.put("message", "Item returned: \"" + itemTitle + "\"");
        activity.put("at", Instant.now().toString());
        realtime.toRoom("admins", "admin:activity", activity);

        for (String uid : new LinkedHashSet<>(List.of(match.getLostOwner(), match.getFoundOwner()))) {
            notifications.createAndPush(uid, "returned", "Item marked as returned",
                    "Great news — this report has been closed as returned.", null);
            users.findById(uid).ifPresent(u -> email.sendItemReturnedEmail(u.getEmail(), u.getName(), itemTitle));
        }
    }
}
