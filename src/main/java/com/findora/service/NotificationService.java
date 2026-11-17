package com.findora.service;

import com.findora.model.Notification;
import com.findora.repository.NotificationRepository;
import com.findora.web.ApiMapper;
import java.util.List;
import java.util.Map;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private final NotificationRepository notifications;
    private final MongoTemplate mongo;
    private final RealtimeService realtime;

    public NotificationService(NotificationRepository notifications, MongoTemplate mongo, RealtimeService realtime) {
        this.notifications = notifications;
        this.mongo = mongo;
        this.realtime = realtime;
    }

    /** Persists a notification (no socket push). */
    public Notification create(String userId, String type, String title, String body, String link) {
        Notification n = new Notification();
        n.setUser(userId);
        n.setType(type);
        n.setTitle(title);
        n.setBody(body);
        n.setLink(link == null ? "" : link);
        return notifications.save(n);
    }

    /** Persists a notification and pushes it to the user's socket room as `notification:new`. */
    public Notification createAndPush(String userId, String type, String title, String body, String link) {
        Notification n = create(userId, type, title, body, link);
        realtime.toRoom(userId, "notification:new", ApiMapper.notification(n));
        return n;
    }

    public Map<String, Object> list(String userId) {
        List<Map<String, Object>> data = notifications.findTop100ByUserOrderByCreatedAtDesc(userId).stream()
                .map(ApiMapper::notification).toList();
        long unread = notifications.countByUserAndRead(userId, false);
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("success", true);
        out.put("data", data);
        out.put("unreadCount", unread);
        return out;
    }

    public void markRead(String userId, String id) {
        notifications.findByIdAndUser(id, userId).ifPresent(n -> {
            n.setRead(true);
            notifications.save(n);
        });
    }

    public void markAllRead(String userId) {
        mongo.updateMulti(
                Query.query(Criteria.where("user").is(userId).and("read").is(false)),
                new Update().set("read", true),
                Notification.class);
    }
}
