package com.findora.socket;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import jakarta.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Socket.IO events, 1:1 with the old server.ts: presence, rooms, typing, read receipts, live location
 * relay and WebRTC call signalling. Event names and payloads are unchanged, so the frontend needs no
 * logic changes (only the socket URL, see README).
 *
 * NOTE: like the Node version, sockets are not authenticated - any client can join any user room.
 */
@Component
public class SocketEventHandlers {

    private final SocketIOServer server;
    private final PresenceService presence;
    /** socket session -> userId it joined as (idempotent joins, cleanup on disconnect) */
    private final Map<UUID, String> joined = new ConcurrentHashMap<>();

    public SocketEventHandlers(SocketIOServer server, PresenceService presence) {
        this.server = server;
        this.presence = presence;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @PostConstruct
    void register() {
        server.addEventListener("user:join", String.class, (client, userId, ack) -> {
            if (userId == null || userId.isBlank()) return;
            client.joinRoom(userId);
            String prev = joined.put(client.getSessionId(), userId);
            if (prev == null) presence.markOnline(userId); // repeated joins on one socket count once
            client.sendEvent("presence:snapshot", presence.snapshot());
        });

        server.addEventListener("presence:request", Object.class,
                (client, data, ack) -> client.sendEvent("presence:snapshot", presence.snapshot()));

        // Generic broadcast-room join (admin/police dashboards subscribe to the "admins" room).
        server.addEventListener("room:join", String.class, (client, room, ack) -> {
            if (room != null && !room.isBlank()) client.joinRoom(room);
        });

        server.addEventListener("conversation:join", String.class, (client, id, ack) -> {
            if (id != null && !id.isBlank()) client.joinRoom(id);
        });
        server.addEventListener("conversation:leave", String.class, (client, id, ack) -> {
            if (id != null && !id.isBlank()) client.leaveRoom(id);
        });

        // typing / read receipts: forwarded to everyone else in the conversation room
        server.addEventListener("typing", Map.class, (client, p, ack) -> {
            String conv = str(p, "conversationId");
            if (conv == null) return;
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("userId", p.get("userId"));
            out.put("name", p.get("name"));
            out.put("conversationId", conv);
            toOthers(conv, client, "typing", out);
        });
        server.addEventListener("typing:stop", Map.class, (client, p, ack) -> {
            String conv = str(p, "conversationId");
            if (conv == null) return;
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("userId", p.get("userId"));
            out.put("conversationId", conv);
            toOthers(conv, client, "typing:stop", out);
        });
        server.addEventListener("message:read", Map.class, (client, p, ack) -> {
            String conv = str(p, "conversationId");
            if (conv == null) return;
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("conversationId", conv);
            out.put("userId", p.get("userId"));
            toOthers(conv, client, "message:read", out);
        });

        // live location: relayed, never persisted
        server.addEventListener("location:share", Map.class, (client, p, ack) -> {
            String conv = str(p, "conversationId");
            if (conv != null) toOthers(conv, client, "location:share", p);
        });
        server.addEventListener("location:stop", Map.class, (client, p, ack) -> {
            String conv = str(p, "conversationId");
            if (conv != null) toOthers(conv, client, "location:stop", p);
        });

        // WebRTC signalling relay: forward to the target user's private room
        for (String event : new String[] {"call:invite", "call:accept", "call:reject", "call:end", "call:signal"}) {
            server.addEventListener(event, Map.class, (client, p, ack) -> {
                String to = str(p, "to");
                if (to != null) server.getRoomOperations(to).sendEvent(event, p);
            });
        }

        server.addDisconnectListener(client -> {
            String userId = joined.remove(client.getSessionId());
            if (userId != null) presence.markOffline(userId);
        });
    }

    private void toOthers(String room, SocketIOClient sender, String event, Object payload) {
        server.getRoomOperations(room).sendEvent(event, sender, payload);
    }

    private static String str(Map<?, ?> m, String key) {
        if (m == null) return null;
        Object v = m.get(key);
        return v == null || String.valueOf(v).isBlank() ? null : String.valueOf(v);
    }
}
