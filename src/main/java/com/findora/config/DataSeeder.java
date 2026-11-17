package com.findora.config;

import com.findora.matching.MatchResult;
import com.findora.matching.MatchingEngine;
import com.findora.model.Bookmark;
import com.findora.model.Coordinates;
import com.findora.model.FoundItem;
import com.findora.model.Item;
import com.findora.model.LostItem;
import com.findora.model.Match;
import com.findora.model.Message;
import com.findora.model.Notification;
import com.findora.model.User;
import com.findora.model.VerificationQuestion;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Demo data (port of seed.ts). WIPES users, reports, matches, notifications, messages and bookmarks, so it
 * only runs when explicitly requested:  java -jar findora-backend.jar --findora.seed=true
 */
@Component
@ConditionalOnProperty(name = "findora.seed", havingValue = "true")
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private final MongoTemplate mongo;
    private final PasswordEncoder encoder;
    private final ConfigurableApplicationContext ctx;

    public DataSeeder(MongoTemplate mongo, PasswordEncoder encoder, ConfigurableApplicationContext ctx) {
        this.mongo = mongo;
        this.encoder = encoder;
        this.ctx = ctx;
    }

    private User user(String name, String email, String password, String city, String role, int points, String badge) {
        User u = new User();
        u.setName(name);
        u.setEmail(email);
        u.setPassword(encoder.encode(password));
        u.setCity(city);
        u.setRole(role);
        u.setVerified(true);
        u.setTrustPoints(points);
        u.setBadge(badge);
        return mongo.save(u);
    }

    private <T extends Item> T item(T i, String title, String category, String description, String color, String brand,
                                    String location, String city, int daysAgo, double reward, String owner) {
        i.setTitle(title);
        i.setCategory(category);
        i.setDescription(description);
        i.setColor(color);
        i.setBrand(brand);
        i.setLocation(location);
        i.setCity(city);
        i.setCoordinates(new Coordinates(null, null));
        i.setDate(Instant.now().minus(Duration.ofDays(daysAgo)));
        i.setReward(reward);
        i.setOwner(owner);
        return mongo.save(i);
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Clearing existing data...");
        for (Class<?> c : List.of(User.class, LostItem.class, FoundItem.class, Match.class, Notification.class,
                Message.class, Bookmark.class)) {
            mongo.remove(new Query(), c);
        }

        User arjun = user("Arjun Sharma", "arjun@example.com", "password123", "Bhopal", "user", 40, "silver");
        User priya = user("Priya Sharma", "priya@example.com", "password123", "Bhopal", "user", 25, "bronze");
        User ramesh = user("Ramesh Yadav", "ramesh@example.com", "password123", "Bhopal", "user", 15, "bronze");
        user("Admin", "admin@findora.app", "admin12345", "", "admin", 0, "none");
        user("Bhopal Police Desk", "police@findora.app", "police12345", "", "police", 0, "none");

        LostItem lostWallet = new LostItem();
        lostWallet.setVerificationQuestions(List.of(
                new VerificationQuestion("What brand is the wallet?", "Wildcraft"),
                new VerificationQuestion("Roughly how much cash was inside?", "1500")));
        item(lostWallet, "Black Wallet", "Wallet", "Black leather wallet, has a PAN card and around ₹1500 cash",
                "Black", "Wildcraft", "DB Mall, Food Court", "Bhopal", 2, 1000, arjun.getId());

        item(new FoundItem(), "Black Wallet", "Wallet",
                "Found a black leather wallet near the food court, has a PAN card and some cash",
                "Black", "Wildcraft", "DB Mall, near parking", "Bhopal", 1, 0, priya.getId());
        item(new FoundItem(), "iPhone 13", "Mobile", "Found near MP Nagar bus stand, screen has a small crack",
                "Blue", "Apple", "MP Nagar", "Bhopal", 3, 0, ramesh.getId());

        LostItem backpack = new LostItem();
        backpack.setStatus("closed");
        item(backpack, "Red Backpack", "Bag", "Red backpack with a laptop and charger inside, lost at the bus stand",
                "Red", "Wildcraft", "Bus Stand", "Bhopal", 4, 0, priya.getId());
        item(new LostItem(), "Car Keys", "Keys", "Toyota key fob with a small blue keychain",
                "Silver", "Toyota", "Kolar Road", "Bhopal", 30, 0, arjun.getId());

        log.info("Computing matches...");
        List<LostItem> lostItems = mongo.find(Query.query(org.springframework.data.mongodb.core.query.Criteria
                .where("status").is("open")), LostItem.class);
        List<FoundItem> foundItems = mongo.find(Query.query(org.springframework.data.mongodb.core.query.Criteria
                .where("status").is("open")), FoundItem.class);
        for (LostItem lost : lostItems) {
            for (FoundItem found : foundItems) {
                MatchResult r = MatchingEngine.compute(lost, found);
                if (r.score() < MatchingEngine.MATCH_THRESHOLD) continue;
                Match m = new Match();
                m.setLostItem(lost.getId());
                m.setFoundItem(found.getId());
                m.setLostOwner(lost.getOwner());
                m.setFoundOwner(found.getOwner());
                m.setScore(r.score());
                m.setBreakdown(r.breakdown());
                mongo.save(m);
            }
        }

        notification(arjun.getId(), "match", "Possible match found", "A likely match was found for \"Black Wallet\"");
        notification(priya.getId(), "claim", "Claim request", "Arjun Sharma wants to verify your found wallet report");

        log.info("Seed complete. Demo logins:");
        log.info("  user:   arjun@example.com   / password123");
        log.info("  user:   priya@example.com   / password123");
        log.info("  admin:  admin@findora.app   / admin12345");
        log.info("  police: police@findora.app  / police12345");
        System.exit(SpringApplication.exit(ctx));
    }

    private void notification(String userId, String type, String title, String body) {
        Notification n = new Notification();
        n.setUser(userId);
        n.setType(type);
        n.setTitle(title);
        n.setBody(body);
        n.setLink("/matches");
        mongo.save(n);
    }
}
