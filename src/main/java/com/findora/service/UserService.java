package com.findora.service;

import com.findora.model.Bookmark;
import com.findora.model.FoundItem;
import com.findora.model.Item;
import com.findora.model.LostItem;
import com.findora.model.User;
import com.findora.repository.BookmarkRepository;
import com.findora.repository.UserRepository;
import com.findora.security.AuthUser;
import com.findora.web.ApiException;
import com.findora.web.ApiMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository users;
    private final BookmarkRepository bookmarks;
    private final MongoTemplate mongo;

    public UserService(UserRepository users, BookmarkRepository bookmarks, MongoTemplate mongo) {
        this.users = users;
        this.bookmarks = bookmarks;
        this.mongo = mongo;
    }

    /** Top 20 regular members by trust points. */
    public List<Map<String, Object>> leaderboard() {
        return users.findTop20ByRoleOrderByTrustPointsDesc(User.ROLE_USER).stream()
                .map(ApiMapper::leaderboardEntry).toList();
    }

    /** Everything the user bookmarked, newest report first, each tagged with kind = lost|found. */
    public List<Map<String, Object>> listSaved(AuthUser user) {
        List<Bookmark> list = bookmarks.findByUserOrderByCreatedAtDesc(user.id());
        Set<String> lostIds = list.stream().filter(b -> "lost".equals(b.getItemType()))
                .map(Bookmark::getItem).collect(Collectors.toSet());
        Set<String> foundIds = list.stream().filter(b -> "found".equals(b.getItemType()))
                .map(Bookmark::getItem).collect(Collectors.toSet());

        List<Item> lost = lostIds.isEmpty() ? List.of()
                : new ArrayList<>(mongo.find(Query.query(Criteria.where("id").in(lostIds)), LostItem.class));
        List<Item> found = foundIds.isEmpty() ? List.of()
                : new ArrayList<>(mongo.find(Query.query(Criteria.where("id").in(foundIds)), FoundItem.class));

        Set<String> ownerIds = new java.util.HashSet<>();
        lost.forEach(i -> ownerIds.add(i.getOwner()));
        found.forEach(i -> ownerIds.add(i.getOwner()));
        Map<String, User> owners = new HashMap<>();
        users.findAllById(ownerIds).forEach(u -> owners.put(u.getId(), u));

        record Tagged(Item item, String kind) {}
        List<Tagged> all = new ArrayList<>();
        lost.forEach(i -> all.add(new Tagged(i, "lost")));
        found.forEach(i -> all.add(new Tagged(i, "found")));
        all.sort(Comparator.comparing((Tagged t) -> t.item().getCreatedAt() == null ? Instant.EPOCH : t.item().getCreatedAt())
                .reversed());

        List<Map<String, Object>> out = new ArrayList<>();
        for (Tagged t : all) {
            Map<String, Object> m = new LinkedHashMap<>(ApiMapper.item(t.item(),
                    ApiMapper.basicOwner(owners.get(t.item().getOwner())), user.id().equals(t.item().getOwner())));
            m.put("kind", t.kind());
            out.add(m);
        }
        return out;
    }

    /** @return true if the item is now saved, false if the bookmark was removed */
    public boolean toggleSaved(AuthUser user, String itemType, String itemId) {
        if (!"lost".equals(itemType) && !"found".equals(itemType)) {
            throw ApiException.badRequest("itemType must be 'lost' or 'found'");
        }
        if (!Validation.validObjectId(itemId)) throw ApiException.badRequest("Invalid item id");

        var existing = bookmarks.findByUserAndItemTypeAndItem(user.id(), itemType, itemId);
        if (existing.isPresent()) {
            bookmarks.delete(existing.get());
            return false;
        }
        Bookmark b = new Bookmark();
        b.setUser(user.id());
        b.setItemType(itemType);
        b.setItem(itemId);
        bookmarks.save(b);
        return true;
    }

    /** Lightweight "lost:<id>" / "found:<id>" list for UI state. */
    public List<String> savedIds(AuthUser user) {
        return bookmarks.findByUserOrderByCreatedAtDesc(user.id()).stream()
                .map(b -> b.getItemType() + ":" + b.getItem()).toList();
    }
}
