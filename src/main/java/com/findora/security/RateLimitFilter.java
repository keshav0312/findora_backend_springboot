package com.findora.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.findora.web.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/** 300 requests per 15 minutes per client IP on /api/** (fixed window, in memory) - mirrors express-rate-limit. */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 15 * 60 * 1000L;
    private static final int LIMIT = 300;

    private static final class Window {
        final long startedAt;
        final AtomicInteger count = new AtomicInteger();

        Window(long startedAt) {
            this.startedAt = startedAt;
        }
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final ObjectMapper mapper;
    private volatile long lastSweep = System.currentTimeMillis();

    public RateLimitFilter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = System.currentTimeMillis();
        sweep(now);

        Window w = windows.compute(request.getRemoteAddr(), (ip, cur) -> cur == null || now - cur.startedAt >= WINDOW_MS
                ? new Window(now) : cur);
        int used = w.count.incrementAndGet();
        int remaining = Math.max(0, LIMIT - used);
        response.setHeader("RateLimit-Limit", String.valueOf(LIMIT));
        response.setHeader("RateLimit-Remaining", String.valueOf(remaining));

        if (used > LIMIT) {
            long retryAfter = Math.max(1, (w.startedAt + WINDOW_MS - now) / 1000);
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            mapper.writeValue(response.getWriter(), ApiResponse.error("Too many requests, please try again later."));
            return;
        }
        chain.doFilter(request, response);
    }

    private void sweep(long now) {
        if (now - lastSweep < WINDOW_MS) return;
        lastSweep = now;
        windows.entrySet().removeIf(e -> now - e.getValue().startedAt >= WINDOW_MS);
    }
}
