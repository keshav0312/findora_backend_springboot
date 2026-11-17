package com.findora.web;

import com.findora.service.AdminService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Role rules live in SecurityConfig: everything here is admin+police, /users* is admin only. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    public record StaffRequest(String name, String email, String password, String role) {}

    private final AdminService admin;

    public AdminController(AdminService admin) {
        this.admin = admin;
    }

    @GetMapping("/analytics")
    public ResponseEntity<Map<String, Object>> analytics() {
        return ApiResponse.ok(admin.analytics());
    }

    @GetMapping("/reports")
    public ResponseEntity<Map<String, Object>> reports() {
        return ApiResponse.ok(admin.recentReports());
    }

    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> users() {
        return ApiResponse.ok(admin.listUsers());
    }

    @PostMapping("/users")
    public ResponseEntity<Map<String, Object>> createStaff(@RequestBody(required = false) StaffRequest r) {
        StaffRequest b = r == null ? new StaffRequest(null, null, null, null) : r;
        Map<String, Object> created = admin.createStaff(b.name(), b.email(), b.password(), b.role());
        return ApiResponse.created(("admin".equals(b.role()) ? "Admin" : "Police") + " account created", created);
    }

    @PatchMapping("/users/{id}/ban")
    public ResponseEntity<Map<String, Object>> ban(@PathVariable String id) {
        return ApiResponse.ok("User restricted", admin.banUser(id));
    }
}
