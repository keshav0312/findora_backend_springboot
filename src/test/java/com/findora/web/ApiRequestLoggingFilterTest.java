package com.findora.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiRequestLoggingFilterTest {

    private final ApiRequestLoggingFilter filter = new ApiRequestLoggingFilter();

    @Test
    void addsRequestIdHeaderAndClearsMdcAfterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> ((HttpServletResponse) res).setStatus(200));

        String requestId = response.getHeader(ApiRequestLoggingFilter.REQUEST_ID_HEADER);
        assertNotNull(requestId);
        assertEquals(requestId, UUID.fromString(requestId).toString());
        assertNull(MDC.get(ApiRequestLoggingFilter.REQUEST_ID_MDC_KEY));
    }

    @Test
    void restoresPreviousMdcWhenRequestFails() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MDC.put(ApiRequestLoggingFilter.REQUEST_ID_MDC_KEY, "parent-request");
        try {
            assertThrows(ServletException.class, () -> filter.doFilter(request, response, (req, res) -> {
                throw new ServletException("expected test failure");
            }));
            assertEquals("parent-request", MDC.get(ApiRequestLoggingFilter.REQUEST_ID_MDC_KEY));
        } finally {
            MDC.remove(ApiRequestLoggingFilter.REQUEST_ID_MDC_KEY);
        }
    }
}
