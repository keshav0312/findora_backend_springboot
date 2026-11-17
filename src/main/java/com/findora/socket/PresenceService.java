package com.findora.socket;

import com.findora.model.User;
import com.findora.service.RealtimeService;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Who is online right now. A user may have several tabs/devices, so connections are reference counted;
 * when the count hits zero we wait a grace period (page refresh/reconnect) before broadcasting "offline"
 * and stamping lastSeen.
 */
@Service
public class PresenceService {

    private static final Logger log = LoggerFactory.getLogger(PresenceService.class);

    private static final long OFFLINE_GRACE_MS = 8000;

    private final RealtimeService realtime;
    private final MongoTemplate mongo;
    private final Map<String, Integer> onlineCounts = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> offlineTimers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public PresenceService(RealtimeService realtime, MongoTemplate mongo) {
        this.realtime = realtime;
        this.mongo = mongo;
    }

    private void broadcast(String userId, boolean online) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", userId);
        m.put("online", online);
        m.put("at", Instant.now().toString());
        realtime.toAll("presence:update", m);
    }

    public synchronized void markOnline(String userId) {
        ScheduledFuture<?> pending = offlineTimers.remove(userId);
        if (pending != null) pending.cancel(false);
        int count = onlineCounts.getOrDefault(userId, 0);
        onlineCounts.put(userId, count + 1);
        if (count == 0) broadcast(userId, true);
    }

    public synchronized void markOffline(String userId) {
        int next = Math.max(0, onlineCounts.getOrDefault(userId, 0) - 1);
        if (next > 0) {
            onlineCounts.put(userId, next);
            return;
        }
        onlineCounts.remove(userId);
        offlineTimers.put(userId, scheduler.schedule(() -> finishOffline(userId), OFFLINE_GRACE_MS, TimeUnit.MILLISECONDS));
    }

    private synchronized void finishOffline(String userId) {
        offlineTimers.remove(userId);
        if (onlineCounts.containsKey(userId)) return;
        broadcast(userId, false);
        try {
            mongo.updateFirst(Query.query(Criteria.where("id").is(userId)),
                    new Update().set("lastSeen", Instant.now()), User.class);
        } catch (Exception e) {
            log.warn("Could not update last-seen timestamp after disconnect", e);
        }
    }

    public List<String> snapshot() {
        return new ArrayList<>(onlineCounts.keySet());
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }
}
