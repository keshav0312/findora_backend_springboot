package com.findora.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.findora.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class JwtAuthFilterTest {

    private final JwtAuthFilter filter = new JwtAuthFilter(mock(JwtService.class), mock(UserRepository.class));

    @Test
    void skipsPublicHealthGetRequests() {
        assertTrue(filter.shouldNotFilter(request("GET", "/")));
        assertTrue(filter.shouldNotFilter(request("GET", "/health")));
        assertTrue(filter.shouldNotFilter(request("GET", "/api/health")));
    }

    @Test
    void doesNotSkipProtectedPathsOrOtherMethods() {
        assertFalse(filter.shouldNotFilter(request("GET", "/api/users/me")));
        assertFalse(filter.shouldNotFilter(request("POST", "/api/health")));
    }

    private static HttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(method);
        request.setServletPath(path);
        return request;
    }
}
