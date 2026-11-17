package com.findora.web;

import com.findora.security.AuthUser;
import com.findora.service.NotificationService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public Map<String, Object> list(@AuthenticationPrincipal AuthUser user) {
        return notifications.list(user.id());
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Map<String, Object>> markAllRead(@AuthenticationPrincipal AuthUser user) {
        notifications.markAllRead(user.id());
        return ApiResponse.message("All notifications marked as read");
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Map<String, Object>> markRead(@PathVariable String id, @AuthenticationPrincipal AuthUser user) {
        notifications.markRead(user.id(), id);
        return ApiResponse.message("Marked as read");
    }
}
