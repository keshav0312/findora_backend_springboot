package com.findora.service;

import com.findora.model.User;
import com.findora.repository.UserRepository;
import com.findora.security.JwtService;
import com.findora.web.ApiException;
import com.findora.web.ApiMapper;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final EmailService email;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt, EmailService email) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.email = email;
    }

    private static String newToken() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    private Map<String, Object> session(User u) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user", ApiMapper.authUser(u));
        data.put("accessToken", jwt.generateAccessToken(u.getId(), u.getRole()));
        data.put("refreshToken", jwt.generateRefreshToken(u.getId(), u.getRole()));
        return data;
    }

    public Map<String, Object> register(String name, String rawEmail, String password, String city) {
        if (Validation.blank(name) || Validation.blank(rawEmail) || Validation.blank(password)) {
            throw ApiException.badRequest("Name, email and password are required");
        }
        if (password.length() < 6) throw ApiException.badRequest("Password must be at least 6 characters");
        String mail = Validation.normalizeEmail(rawEmail);
        if (!Validation.validEmail(mail)) throw ApiException.badRequest("Please provide a valid email address");
        if (users.findByEmail(mail).isPresent()) {
            throw ApiException.conflict("An account with this email already exists");
        }

        User u = new User();
        u.setName(name.trim());
        u.setEmail(mail);
        u.setPassword(encoder.encode(password));
        u.setCity(city == null ? "" : city.trim());
        // Verification token (24h). Sending never blocks or fails registration.
        u.setEmailVerificationToken(newToken());
        u.setEmailVerificationExpires(Instant.now().plus(Duration.ofHours(24)));
        u = users.save(u);
        email.sendVerificationEmail(u.getEmail(), u.getName(), u.getEmailVerificationToken());
        return session(u);
    }

    public Map<String, Object> login(String rawEmail, String password) {
        if (Validation.blank(rawEmail) || Validation.blank(password)) {
            throw ApiException.badRequest("Email and password are required");
        }
        User u = users.findByEmail(Validation.normalizeEmail(rawEmail)).orElse(null);
        if (u == null || u.getPassword() == null || !encoder.matches(password, u.getPassword())) {
            throw ApiException.unauthorized("Invalid email or password");
        }
        return session(u);
    }

    public Map<String, Object> me(String userId) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        return ApiMapper.authUser(u);
    }

    public Map<String, Object> updateProfile(String userId, String name, String city, String phone, String avatar) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        if (name != null) {
            if (name.isBlank()) throw ApiException.badRequest("Name cannot be empty");
            u.setName(name.trim());
        }
        if (city != null) u.setCity(city.trim());
        if (phone != null) u.setPhone(phone.trim());
        if (avatar != null) u.setAvatar(avatar);
        return ApiMapper.authUser(users.save(u));
    }

    public void forgotPassword(String rawEmail) {
        if (Validation.blank(rawEmail)) return;
        // Same response whether or not the address exists, so registered emails don't leak.
        users.findByEmail(Validation.normalizeEmail(rawEmail)).ifPresent(u -> {
            u.setResetPasswordToken(newToken());
            u.setResetPasswordExpires(Instant.now().plus(Duration.ofMinutes(30)));
            users.save(u);
            email.sendPasswordResetEmail(u.getEmail(), u.getName(), u.getResetPasswordToken());
        });
    }

    public void resetPassword(String token, String password) {
        if (Validation.blank(token) || Validation.blank(password)) {
            throw ApiException.badRequest("Token and new password are required");
        }
        if (password.length() < 6) throw ApiException.badRequest("Password must be at least 6 characters");
        User u = users.findByResetPasswordTokenAndResetPasswordExpiresAfter(token, Instant.now())
                .orElseThrow(() -> ApiException.badRequest("Invalid or expired reset link"));
        u.setPassword(encoder.encode(password));
        u.setResetPasswordToken(null);
        u.setResetPasswordExpires(null);
        users.save(u);
    }

    public void verifyEmail(String token) {
        if (Validation.blank(token)) throw ApiException.badRequest("Verification token is required");
        User u = users.findByEmailVerificationTokenAndEmailVerificationExpiresAfter(token, Instant.now())
                .orElseThrow(() -> ApiException.badRequest("Invalid or expired verification link"));
        u.setVerified(true);
        u.setEmailVerificationToken(null);
        u.setEmailVerificationExpires(null);
        users.save(u);
    }

    /** @return true if a new email was issued, false if the account was already verified */
    public boolean resendVerification(String userId) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        if (Boolean.TRUE.equals(u.getVerified())) return false;
        u.setEmailVerificationToken(newToken());
        u.setEmailVerificationExpires(Instant.now().plus(Duration.ofHours(24)));
        users.save(u);
        email.sendVerificationEmail(u.getEmail(), u.getName(), u.getEmailVerificationToken());
        return true;
    }
}
