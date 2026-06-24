package com.example.forklift_erp.util;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

public final class BusinessNumberGenerator {
    private static final DateTimeFormatter BUSINESS_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private BusinessNumberGenerator() {
    }

    public static String next(String prefix, int suffixLength) {
        return next(prefix, suffixLength, Clock.systemDefaultZone());
    }

    static String next(String prefix, int suffixLength, Clock clock) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("Business number prefix is required");
        }
        if (suffixLength < 1 || suffixLength > 32) {
            throw new IllegalArgumentException("Business number suffix length must be between 1 and 32");
        }
        String suffix = UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, suffixLength)
                .toUpperCase(Locale.ROOT);
        return prefix.trim().toUpperCase(Locale.ROOT) + "-"
                + BUSINESS_NO_TIME.format(LocalDateTime.now(clock)) + "-"
                + suffix;
    }
}
