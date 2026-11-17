package com.findora.service;

import com.findora.model.Match;
import com.findora.model.Message;
import com.findora.model.MessageAttachment;
import com.findora.model.MessageLocation;
import com.findora.repository.MatchRepository;
import com.findora.repository.MessageRepository;
import com.findora.security.AuthUser;
import com.findora.web.ApiException;
import com.findora.web.ApiMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Chat between the two parties of a match. Conversation id is always "match_<matchId>". */
@Service
public class MessageService {

    private final MessageRepository messages;
    private final MatchRepository matches;
    private final MongoTemplate mongo;
    private final NotificationService notifications;
    private final RealtimeService realtime;
    private final FileStorageService storage;

    public MessageService(MessageRepository messages, MatchRepository matches, MongoTemplate mongo,
                          NotificationService notifications, RealtimeService realtime, FileStorageService storage) {
        this.messages = messages;
        this.matches = matches;
        this.mongo = mongo;
        this.notifications = notifications;
        this.realtime = realtime;
        this.storage = storage;
    }

    static String conversationId(String matchId) {
        return "match_" + matchId;
    }

    private Match requireMatch(String matchId) {
        Match m = Validation.validObjectId(matchId) ? matches.findById(matchId).orElse(null) : null;
        if (m == null) throw ApiException.notFound("Match not found");
        return m;
    }

    private static boolean isParty(Match m, AuthUser u) {
        return u.id().equals(m.getLostOwner()) || u.id().equals(m.getFoundOwner());
    }

    /** Sender must belong to the match, and the recipient must be the other party. */
    private Match requireSendable(String matchId, AuthUser sender, String recipient) {
        Match m = requireMatch(matchId);
        if (!isParty(m, sender)) throw ApiException.forbidden("Not part of this match");
        String counterpart = sender.id().equals(m.getLostOwner()) ? m.getFoundOwner() : m.getLostOwner();
        if (!recipient.equals(counterpart)) throw ApiException.badRequest("Recipient is not part of this match");
        return m;
    }

    private static String preview(String text) {
        int[] cps = text.codePoints().limit(80).toArray();
        return new String(cps, 0, cps.length);
    }

    public List<Map<String, Object>> getConversation(String matchId, AuthUser user) {
        Match m = requireMatch(matchId);
        if (!isParty(m, user) && !user.isPrivileged()) throw ApiException.forbidden("Not part of this match");
        return messages.findByConversationIdOrderByCreatedAtAsc(conversationId(matchId)).stream()
                .map(ApiMapper::message).toList();
    }

    public Map<String, Object> sendMessage(AuthUser sender, String matchId, String recipient, String text) {
        if (Validation.blank(text) || Validation.blank(recipient)) {
            throw ApiException.badRequest("recipient and text are required");
        }
        if (Validation.blank(matchId)) throw ApiException.badRequest("matchId is required");
        requireSendable(matchId, sender, recipient);

        Message msg = newMessage(sender, matchId, recipient);
        msg.setText(text);
        return deliver(msg, matchId, preview(text));
    }

    public Map<String, Object> sendAttachment(AuthUser sender, String matchId, String recipient, String text,
                                              String kind, String durationSeconds, MultipartFile file) {
        if (file == null || file.isEmpty() || Validation.blank(recipient) || Validation.blank(matchId)) {
            throw ApiException.badRequest("file, recipient and matchId are required");
        }
        requireSendable(matchId, sender, recipient);

        String attachmentKind = "audio".equals(kind) ? "audio" : "image";
        String url = storage.storeChatAttachment(file);
        int duration = 0;
        try {
            if (durationSeconds != null && !durationSeconds.isBlank()) duration = (int) Math.round(Double.parseDouble(durationSeconds));
        } catch (NumberFormatException ignored) { }

        Message msg = newMessage(sender, matchId, recipient);
        msg.setText(text == null ? "" : text);
        msg.setAttachment(new MessageAttachment(attachmentKind, url, duration));
        return deliver(msg, matchId, "audio".equals(attachmentKind) ? "Sent a voice note" : "Sent a photo");
    }

    public Map<String, Object> sendLocation(AuthUser sender, String matchId, String recipient,
                                            Object lat, Object lng, String label) {
        if (Validation.blank(recipient) || Validation.blank(matchId) || lat == null || lng == null) {
            throw ApiException.badRequest("matchId, recipient, lat and lng are required");
        }
        double latitude;
        double longitude;
        try {
            latitude = Double.parseDouble(String.valueOf(lat));
            longitude = Double.parseDouble(String.valueOf(lng));
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("lat and lng must be valid numbers");
        }
        if (Double.isNaN(latitude) || Double.isNaN(longitude) || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) {
            throw ApiException.badRequest("lat and lng must be valid numbers");
        }
        requireSendable(matchId, sender, recipient);

        Message msg = newMessage(sender, matchId, recipient);
        msg.setLocation(new MessageLocation(latitude, longitude, label == null ? "" : label));
        return deliver(msg, matchId, "Shared a live location");
    }

    private Message newMessage(AuthUser sender, String matchId, String recipient) {
        Message msg = new Message();
        msg.setConversationId(conversationId(matchId));
        msg.setSender(sender.id());
        msg.setRecipient(recipient);
        msg.setMatchId(matchId);
        return msg;
    }

    /** Saves, records a notification and pushes both the message and a live notification over the socket. */
    private Map<String, Object> deliver(Message msg, String matchId, String notificationBody) {
        msg = messages.save(msg);
        notifications.create(msg.getRecipient(), "message", "New message", notificationBody, "/chat/" + matchId);

        Map<String, Object> json = ApiMapper.message(msg);
        realtime.toRoom(msg.getConversationId(), "message:new", json);
        realtime.toRoom(msg.getRecipient(), "notification:new",
                ApiMapper.liveMessageNotification(msg, notificationBody, matchId));
        return json;
    }

    public void markConversationRead(String matchId, AuthUser user) {
        String conversationId = conversationId(matchId);
        mongo.updateMulti(
                Query.query(Criteria.where("conversationId").is(conversationId)
                        .and("recipient").is(user.id()).and("read").is(false)),
                new Update().set("read", true),
                Message.class);
        realtime.toRoom(conversationId, "message:read", Map.of("conversationId", conversationId, "userId", user.id()));
    }

    /** Latest message of each conversation the user takes part in, newest first. */
    public List<Map<String, Object>> myConversations(AuthUser user) {
        Set<String> seen = new HashSet<>();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Message m : messages.findBySenderOrRecipientOrderByCreatedAtDesc(user.id(), user.id())) {
            if (seen.add(m.getConversationId())) out.add(ApiMapper.message(m));
        }
        return out;
    }
}
