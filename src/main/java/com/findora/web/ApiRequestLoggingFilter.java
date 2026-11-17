package com.findora.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

public class ApiRequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiRequestLoggingFilter.class);
    static final String REQUEST_ID_HEADER = "X-Request-ID";
    static final String REQUEST_ID_MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        String previousRequestId = MDC.get(REQUEST_ID_MDC_KEY);
        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        long startedAt = System.nanoTime();
        boolean failed = false;
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException e) {
            failed = true;
            log.error("HTTP request processing failed method={} path={} requestId={} durationMs={}",
                    request.getMethod(), request.getRequestURI(), requestId, elapsedMillis(startedAt), e);
            throw e;
        } finally {
            if (!failed) {
                logResponse(request, response, requestId, elapsedMillis(startedAt));
            }
            if (previousRequestId == null) {
                MDC.remove(REQUEST_ID_MDC_KEY);
            } else {
                MDC.put(REQUEST_ID_MDC_KEY, previousRequestId);
            }
        }
    }

    private static void logResponse(
            HttpServletRequest request, HttpServletResponse response, String requestId, long durationMs) {
        String message = "HTTP request completed method={} path={} status={} requestId={} durationMs={}";
        Object[] details = {
                request.getMethod(), request.getRequestURI(), response.getStatus(), requestId, durationMs
        };
        if (response.getStatus() >= HttpServletResponse.SC_INTERNAL_SERVER_ERROR) {
            log.error(message, details);
        } else if (response.getStatus() >= HttpServletResponse.SC_BAD_REQUEST) {
            log.warn(message, details);
        } else {
            log.info(message, details);
        }
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
