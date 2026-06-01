package com.example.forklift_erp.constant;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public final class EnumCodes {

    private EnumCodes() {
    }

    public static <E extends Enum<E> & CodedEnum> boolean isValid(Class<E> type, String value) {
        return parse(type, value) != null;
    }

    public static <E extends Enum<E> & CodedEnum> String normalizeOrDefault(Class<E> type, String value, E fallback) {
        E parsed = parse(type, value);
        return (parsed == null ? fallback : parsed).code();
    }

    public static <E extends Enum<E> & CodedEnum> String normalizeOrThrow(Class<E> type, String value, String message) {
        E parsed = parse(type, value);
        if (parsed == null) {
            throw new IllegalArgumentException(message);
        }
        return parsed.code();
    }

    public static <E extends Enum<E> & CodedEnum> String validationPattern(Class<E> type) {
        return Arrays.stream(type.getEnumConstants())
                .map(CodedEnum::code)
                .collect(Collectors.joining("|", "^(", ")$"));
    }

    public static <E extends Enum<E> & CodedEnum> E parse(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(type.getEnumConstants())
                .filter(item -> item.code().equals(normalized))
                .findFirst()
                .orElse(null);
    }
}
