package com.findora.web;

import com.findora.security.AuthUser;
import com.findora.service.AuthService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record RegisterRequest(String name, String email, String password, String city) {}
    public record LoginRequest(String email, String password) {}
    public record ForgotRequest(String email) {}
    public record ResetRequest(String token, String password) {}
    public record ProfileRequest(String name, String city, String phone, String avatar) {}

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody(required = false) RegisterRequest r) {
        RegisterRequest b = r == null ? new RegisterRequest(null, null, null, null) : r;
        return ApiResponse.created("Account created — check your email to verify your account",
                auth.register(b.name(), b.email(), b.password(), b.city()));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody(required = false) LoginRequest r) {
        LoginRequest b = r == null ? new LoginRequest(null, null) : r;
        return ApiResponse.ok("Logged in", auth.login(b.email(), b.password()));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, Object>> forgot(@RequestBody(required = false) ForgotRequest r) {
        auth.forgotPassword(r == null ? null : r.email());
        return ApiResponse.message("If an account exists for this email, a reset link has been sent");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, Object>> reset(@RequestBody(required = false) ResetRequest r) {
        ResetRequest b = r == null ? new ResetRequest(null, null) : r;
        auth.resetPassword(b.token(), b.password());
        return ApiResponse.message("Password reset — you can now log in");
    }

    /** Token may arrive in the JSON body (POST) or the query string (GET link from the email). */
    @RequestMapping(value = "/verify-email", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Map<String, Object>> verifyEmail(@RequestParam(required = false) String token,
                                                           @RequestBody(required = false) Map<String, Object> body) {
        String t = body != null && body.get("token") != null ? String.valueOf(body.get("token")) : token;
        auth.verifyEmail(t);
        return ApiResponse.message("Email verified successfully");
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Map<String, Object>> resend(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.message(auth.resendVerification(user.id())
                ? "Verification email sent" : "Email is already verified");
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(auth.me(user.id()));
    }

    @PatchMapping("/me")
    public ResponseEntity<Map<String, Object>> updateProfile(@AuthenticationPrincipal AuthUser user,
                                                             @RequestBody(required = false) ProfileRequest r) {
        ProfileRequest b = r == null ? new ProfileRequest(null, null, null, null) : r;
        return ApiResponse.ok("Profile updated", auth.updateProfile(user.id(), b.name(), b.city(), b.phone(), b.avatar()));
    }
}
