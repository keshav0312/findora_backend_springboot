package com.findora.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.findora.config.AppProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.springframework.stereotype.Service;

/**
 * HS256 access (15m) / refresh (30d) tokens with `id` and `role` claims - the same format the Node app
 * issued with jsonwebtoken, so with the same secrets previously issued tokens keep working.
 */
@Service
public class JwtService {

    private static final Duration ACCESS_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TTL = Duration.ofDays(30);

    private final Algorithm accessAlg;
    private final Algorithm refreshAlg;

    public JwtService(AppProperties props) {
        String access = props.jwt().secret();
        String refresh = props.jwt().refreshSecret();
        if (access == null || access.isBlank() || refresh == null || refresh.isBlank()) {
            throw new IllegalStateException(
                    "findora.jwt.secret and findora.jwt.refresh-secret must be set in application.yml or the environment.");
        }
        this.accessAlg = Algorithm.HMAC256(access);
        this.refreshAlg = Algorithm.HMAC256(refresh);
    }

    public String generateAccessToken(String id, String role) {
        return sign(id, role, accessAlg, ACCESS_TTL);
    }

    public String generateRefreshToken(String id, String role) {
        return sign(id, role, refreshAlg, REFRESH_TTL);
    }

    /** @throws JWTVerificationException if the token is invalid or expired */
    public AuthUser verifyAccessToken(String token) {
        DecodedJWT jwt = JWT.require(accessAlg).build().verify(token);
        String id = jwt.getClaim("id").asString();
        if (id == null || id.isBlank()) throw new JWTVerificationException("Missing id claim");
        return new AuthUser(id, jwt.getClaim("role").asString());
    }

    private static String sign(String id, String role, Algorithm alg, Duration ttl) {
        Instant now = Instant.now();
        return JWT.create()
                .withClaim("id", id)
                .withClaim("role", role)
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(now.plus(ttl)))
                .sign(alg);
    }
}
