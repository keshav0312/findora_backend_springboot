package com.findora.security;

/** The authenticated caller, available in controllers via @AuthenticationPrincipal. */
public record AuthUser(String id, String role) {
    public boolean isPrivileged() {
        return !"user".equals(role);
    }
}
