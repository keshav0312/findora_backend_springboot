package com.findora.web;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Builders for the {success, message?, data?, ...} envelope the frontend expects. */
public final class ApiResponse {

    private ApiResponse() {}

    public static Map<String, Object> body(String message, Object data) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", true);
        if (message != null) m.put("message", message);
        if (data != null) m.put("data", data);
        return m;
    }

    public static ResponseEntity<Map<String, Object>> ok(Object data) {
        return ResponseEntity.ok(body(null, data));
    }

    public static ResponseEntity<Map<String, Object>> ok(String message, Object data) {
        return ResponseEntity.ok(body(message, data));
    }

    public static ResponseEntity<Map<String, Object>> message(String message) {
        return ResponseEntity.ok(body(message, null));
    }

    public static ResponseEntity<Map<String, Object>> created(String message, Object data) {
        return ResponseEntity.status(HttpStatus.CREATED).body(body(message, data));
    }

    public static Map<String, Object> error(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", false);
        m.put("message", message);
        return m;
    }
}
