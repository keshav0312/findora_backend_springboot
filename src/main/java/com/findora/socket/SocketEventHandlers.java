package com.findora.socket;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.corundumstudio.socketio.AuthTokenResult;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.findora.model.Match;
import com.findora.model.User;
import com.findora.repository.MatchRepository;
import com.findora.repository.UserRepository;
import com.findora.security.AuthUser;
import com.findora.security.JwtService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/** Authenticated Socket.IO presence, chat, location, and WebRTC event handlers. */
@Component
public class SocketEventHandlers {

    private static final Logger log = LoggerFactory.getLogger(SocketEventHandlers.class);
    private static final String IDENTITY_KEY = "findora.socket.identity";
    private static final String CONVERSATION_KEY_PREFIX = "findora.socket.conversation.";
    private static final String CONVERSATION_PREFIX = "match_";

    private final SocketIOServer server;
    private final PresenceService presence;
    private final JwtService jwtService;
    private final UserRepository users;
    private final MatchRepository matches;
    private final Map<UUID, String> joined = new ConcurrentHashMap<>();
    private final Set<UUID> unauthenticatedConnections = ConcurrentHashMap.newKeySet();

    private record SocketIdentity(AuthUser user, String name, String avatar) {}
    private record ConversationParticipants(String lostOwner, String foundOwner) {}

    public SocketEventHandlers(
            SocketIOServer server,
            PresenceService presence,
            JwtService jwtService,
            UserRepository users,
            MatchRepository matches) {
        this.server = server;
        this.presence = presence;
        this.jwtService = jwtService;
        this.users = users;
        this.matches = matches;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @jakarta.annotation.PostConstruct
    void register() {
        server.getNamespace("").addAuthTokenListener(this::authenticate);
        server.addConnectListener(client -> {
            UUID sessionId = client.getSessionId();
            SocketIdentity socketIdentity = identity(client);
            if (socketIdentity != null) {
                unauthenticatedConnections.remove(sessionId);
                log.info("Socket.IO client connected for user {}", socketIdentity.user().id());
            } else if (!unauthenticatedConnections.add(sessionId)) {
                unauthenticatedConnections.remove(sessionId);
                log.warn("Disconnected Socket.IO client without a valid access token: {}", client.getSessionId());
                client.disconnect();
            }
        });

        server.addEventListener("user:join", String.class, (client, ignored, ack) -> {
            SocketIdentity identity = identity(client);
            if (identity == null) return;
            String userId = identity.user().id();
            client.joinRoom(userId);
            String previous = joined.put(client.getSessionId(), userId);
            if (previous == null) presence.markOnline(userId);
            client.sendEvent("presence:snapshot", presence.snapshot());
        });

        server.addEventListener("presence:request", Object.class,
                (client, ignored, ack) -> {
                    if (identity(client) != null) client.sendEvent("presence:snapshot", presence.snapshot());
                });

        server.addEventListener("room:join", String.class, (client, room, ack) -> {
            SocketIdentity identity = identity(client);
            if (identity != null && "admins".equals(room) && isPrivileged(identity.user())) {
                client.joinRoom(room);
            }
        });

        server.addEventListener("conversation:join", String.class, (client, conversationId, ack) -> {
            SocketIdentity identity = identity(client);
            String matchId = matchId(conversationId);
            if (identity == null || matchId == null) return;
            Match match = matches.findById(matchId).orElse(null);
            if (match == null || !isParticipant(match, identity.user().id())) return;
            client.set(conversationKey(conversationId),
                    new ConversationParticipants(match.getLostOwner(), match.getFoundOwner()));
            client.joinRoom(conversationId);
        });

        server.addEventListener("conversation:leave", String.class, (client, conversationId, ack) -> {
            if (conversationId == null || conversationId.isBlank()) return;
            client.leaveRoom(conversationId);
            String matchId = matchId(conversationId);
            if (matchId != null) client.del(conversationKey(conversationId));
        });

        server.addEventListener("typing", Map.class, (client, payload, ack) ->
                relayConversationEvent(client, payload, "typing", true));
        server.addEventListener("typing:stop", Map.class, (client, payload, ack) ->
                relayConversationEvent(client, payload, "typing:stop", false));
        server.addEventListener("message:read", Map.class, (client, payload, ack) ->
                relayConversationEvent(client, payload, "message:read", false));
        server.addEventListener("location:share", Map.class, (client, payload, ack) -> {
            if (!validLocation(payload)) return;
            relayConversationEvent(client, payload, "location:share", false);
        });
        server.addEventListener("location:stop", Map.class, (client, payload, ack) ->
                relayConversationEvent(client, payload, "location:stop", false));

        for (String event : new String[] {"call:invite", "call:accept", "call:reject", "call:end", "call:signal"}) {
            server.addEventListener(event, Map.class, (client, payload, ack) -> relayCallEvent(client, payload, event));
        }

        server.addDisconnectListener(client -> {
            UUID sessionId = client.getSessionId();
            unauthenticatedConnections.remove(sessionId);
            String userId = joined.remove(sessionId);
            if (userId != null) presence.markOffline(userId);
        });
    }

    private AuthTokenResult authenticate(Object authData, SocketIOClient client) {
        if (!(authData instanceof Map<?, ?> auth) || !(auth.get("token") instanceof String token) || token.isBlank()) {
            return rejected("Authentication required");
        }

        try {
            AuthUser claims = jwtService.verifyAccessToken(token);
            User user = users.findById(claims.id()).orElse(null);
            if (user == null) return rejected("Invalid or expired token");
            client.set(IDENTITY_KEY, new SocketIdentity(
                    new AuthUser(user.getId(), user.getRole()), user.getName(), user.getAvatar()));
            return AuthTokenResult.AuthTokenResultSuccess;
        } catch (JWTVerificationException exception) {
            log.warn("Rejected Socket.IO connection with an invalid or expired access token");
            return rejected("Invalid or expired token");
        } catch (DataAccessException exception) {
            log.error("Could not verify Socket.IO account status", exception);
            return rejected("Authentication temporarily unavailable");
        }
    }

    private void relayConversationEvent(SocketIOClient client, Map<?, ?> payload, String event, boolean includeName) {
        SocketIdentity identity = identity(client);
        String conversationId = string(payload, "conversationId");
        if (identity == null || conversationId == null) return;
        ConversationParticipants participants = authorizedParticipants(client, conversationId, identity);
        if (participants == null) return;

        Map<String, Object> output = copyPayload(payload);
        output.put("conversationId", conversationId);
        output.put("userId", identity.user().id());
        if (includeName) output.put("name", identity.name());
        toOthers(conversationId, client, event, output);
    }

    private void relayCallEvent(SocketIOClient client, Map<?, ?> payload, String event) {
        SocketIdentity identity = identity(client);
        String conversationId = string(payload, "conversationId");
        String recipientId = string(payload, "to");
        if (identity == null || recipientId == null) return;
        ConversationParticipants participants = authorizedParticipants(client, conversationId, identity);
        if (participants == null || !isCounterparty(participants, identity.user().id(), recipientId)) return;

        if ("call:invite".equals(event)) {
            String callType = string(payload, "callType");
            if (!"audio".equals(callType) && !"video".equals(callType)) return;
        } else if ("call:signal".equals(event)) {
            Object data = payload.get("data");
            if (!(data instanceof Map<?, ?> signal)) return;
            String signalType = string(signal, "type");
            if (signalType == null || !Set.of("offer", "answer", "ice").contains(signalType)) return;
        }

        Map<String, Object> output = copyPayload(payload);
        output.put("from", identity.user().id());
        output.put("to", recipientId);
        output.put("conversationId", conversationId);
        if ("call:invite".equals(event)) {
            output.put("fromName", identity.name());
            output.put("fromAvatar", identity.avatar());
        }
        server.getRoomOperations(recipientId).sendEvent(event, output);
    }

    private ConversationParticipants authorizedParticipants(
            SocketIOClient client, String conversationId, SocketIdentity identity) {
        if (matchId(conversationId) == null || !client.getAllRooms().contains(conversationId)) return null;
        Object value = client.get(conversationKey(conversationId));
        if (!(value instanceof ConversationParticipants participants)
                || !isParticipant(participants, identity.user().id())) return null;
        return participants;
    }

    private static boolean validLocation(Map<?, ?> payload) {
        if (payload == null || !(payload.get("lat") instanceof Number lat)
                || !(payload.get("lng") instanceof Number lng)) return false;
        double latitude = lat.doubleValue();
        double longitude = lng.doubleValue();
        return Double.isFinite(latitude) && Double.isFinite(longitude)
                && latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
    }

    private static boolean isParticipant(Match match, String userId) {
        return userId.equals(match.getLostOwner()) || userId.equals(match.getFoundOwner());
    }

    private static boolean isParticipant(ConversationParticipants participants, String userId) {
        return userId.equals(participants.lostOwner()) || userId.equals(participants.foundOwner());
    }

    private static boolean isCounterparty(ConversationParticipants participants, String senderId, String recipientId) {
        return (senderId.equals(participants.lostOwner()) && recipientId.equals(participants.foundOwner()))
                || (senderId.equals(participants.foundOwner()) && recipientId.equals(participants.lostOwner()));
    }

    private static boolean isPrivileged(AuthUser user) {
        return User.ROLE_ADMIN.equals(user.role()) || User.ROLE_POLICE.equals(user.role());
    }

    private static String matchId(String conversationId) {
        if (conversationId == null || !conversationId.startsWith(CONVERSATION_PREFIX)) return null;
        String id = conversationId.substring(CONVERSATION_PREFIX.length());
        return ObjectId.isValid(id) ? id : null;
    }

    private static String conversationKey(String conversationId) {
        return CONVERSATION_KEY_PREFIX + conversationId;
    }

    private static SocketIdentity identity(SocketIOClient client) {
        Object value = client.get(IDENTITY_KEY);
        return value instanceof SocketIdentity identity ? identity : null;
    }

    private static String string(Map<?, ?> payload, String key) {
        if (payload == null) return null;
        Object value = payload.get(key);
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private static Map<String, Object> copyPayload(Map<?, ?> payload) {
        Map<String, Object> output = new LinkedHashMap<>();
        if (payload != null) {
            payload.forEach((key, value) -> {
                if (key instanceof String text) output.put(text, value);
            });
        }
        return output;
    }

    private void toOthers(String room, SocketIOClient sender, String event, Object payload) {
        server.getRoomOperations(room).sendEvent(event, sender, payload);
    }

    private static AuthTokenResult rejected(String message) {
        return new AuthTokenResult(false, Map.of("message", message));
    }
}
