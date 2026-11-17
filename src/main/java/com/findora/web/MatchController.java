package com.findora.web;

import com.findora.security.AuthUser;
import com.findora.service.MatchService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/matches")
public class MatchController {

    private final MatchService matches;

    public MatchController(MatchService matches) {
        this.matches = matches;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> mine(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(matches.myMatches(user));
    }

    /** Body: { "status": "confirmed" | "rejected" | "returned" | "suggested" } */
    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateStatus(@PathVariable String id,
                                                            @AuthenticationPrincipal AuthUser user,
                                                            @RequestBody(required = false) Map<String, Object> body) {
        Object status = body == null ? null : body.get("status");
        return ApiResponse.ok("Match updated", matches.updateStatus(id, user, status == null ? null : String.valueOf(status)));
    }
}
