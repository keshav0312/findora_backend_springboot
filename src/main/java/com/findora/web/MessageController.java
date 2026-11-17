package com.findora.web;

import com.findora.security.AuthUser;
import com.findora.service.MessageService;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    public record SendRequest(String matchId, String recipient, String text) {}

    private final MessageService messages;

    public MessageController(MessageService messages) {
        this.messages = messages;
    }

    private static String s(Map<String, Object> m, String key) {
        Object v = m == null ? null : m.get(key);
        return v == null ? null : String.valueOf(v);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> conversations(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(messages.myConversations(user));
    }

    @GetMapping("/{matchId}")
    public ResponseEntity<Map<String, Object>> conversation(@PathVariable String matchId,
                                                            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(messages.getConversation(matchId, user));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> send(@AuthenticationPrincipal AuthUser user,
                                                    @RequestBody(required = false) SendRequest r) {
        SendRequest b = r == null ? new SendRequest(null, null, null) : r;
        return ApiResponse.created(null, messages.sendMessage(user, b.matchId(), b.recipient(), b.text()));
    }

    /** multipart/form-data: file, matchId, recipient, text?, kind? ("image"|"audio"), durationSeconds? */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> upload(@AuthenticationPrincipal AuthUser user,
                                                      @RequestPart(value = "file", required = false) MultipartFile file,
                                                      @RequestParam(required = false) String matchId,
                                                      @RequestParam(required = false) String recipient,
                                                      @RequestParam(required = false) String text,
                                                      @RequestParam(required = false) String kind,
                                                      @RequestParam(required = false) String durationSeconds) {
        return ApiResponse.created(null,
                messages.sendAttachment(user, matchId, recipient, text, kind, durationSeconds, file));
    }

    /** Body: { matchId, recipient, lat, lng, label? } */
    @PostMapping("/location")
    public ResponseEntity<Map<String, Object>> location(@AuthenticationPrincipal AuthUser user,
                                                        @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.created(null, messages.sendLocation(user, s(body, "matchId"), s(body, "recipient"),
                body == null ? null : body.get("lat"), body == null ? null : body.get("lng"), s(body, "label")));
    }

    @PostMapping("/{matchId}/read")
    public ResponseEntity<Map<String, Object>> markRead(@PathVariable String matchId,
                                                        @AuthenticationPrincipal AuthUser user) {
        messages.markConversationRead(matchId, user);
        return ApiResponse.message("Conversation marked read");
    }
}
