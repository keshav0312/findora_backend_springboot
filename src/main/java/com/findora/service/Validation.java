package com.findora.service;

import java.util.regex.Pattern;
import org.bson.types.ObjectId;

/** Small shared input checks. */
final class Validation {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private Validation() {}

    static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    static boolean validEmail(String email) {
        return email != null && EMAIL.matcher(email).matches();
    }

    static boolean validObjectId(String id) {
        return id != null && ObjectId.isValid(id);
    }

    static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
