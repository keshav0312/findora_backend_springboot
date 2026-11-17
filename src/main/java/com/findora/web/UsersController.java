package com.findora.web;

import com.findora.security.AuthUser;
import com.findora.service.UserService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UsersController {

    private final UserService users;

    public UsersController(UserService users) {
        this.users = users;
    }

    @GetMapping("/leaderboard")
    public ResponseEntity<Map<String, Object>> leaderboard() {
        return ApiResponse.ok(users.leaderboard());
    }

    @GetMapping("/me/saved")
    public ResponseEntity<Map<String, Object>> saved(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(users.listSaved(user));
    }

    @GetMapping("/me/saved/ids")
    public ResponseEntity<Map<String, Object>> savedIds(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(users.savedIds(user));
    }

    /** Toggles a bookmark: 201 {saved:true} when added, 200 {saved:false} when removed. */
    @PostMapping("/me/saved/{itemType}/{itemId}")
    public ResponseEntity<Map<String, Object>> toggle(@AuthenticationPrincipal AuthUser user,
                                                      @PathVariable String itemType, @PathVariable String itemId) {
        boolean saved = users.toggleSaved(user, itemType, itemId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("saved", saved);
        return ResponseEntity.status(saved ? 201 : 200).body(body);
    }
}
