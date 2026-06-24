package com.example.forklift_erp.util;

import java.util.Arrays;
import java.util.stream.Collectors;

public final class SearchKeywordSupport {
    private static final char LIKE_ESCAPE = '!';

    private SearchKeywordSupport() {
    }

    public static String normalize(String keyword) {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }

    public static String likePrefix(String keyword) {
        String normalized = normalize(keyword);
        if (normalized == null) {
            return null;
        }
        return escapeLike(normalized) + "%";
    }

    public static String fullTextBoolean(String keyword) {
        String normalized = normalize(keyword);
        if (normalized == null) {
            return null;
        }
        String query = Arrays.stream(normalized.split("\\s+"))
                .map(SearchKeywordSupport::sanitizeFullTextTerm)
                .filter(term -> !term.isBlank())
                .map(term -> "+" + term + "*")
                .collect(Collectors.joining(" "));
        return query.isBlank() ? null : query;
    }

    private static String escapeLike(String value) {
        return value
                .replace(String.valueOf(LIKE_ESCAPE), "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }

    private static String sanitizeFullTextTerm(String term) {
        return term
                .replaceAll("[+\\-~*<>()@\"']", " ")
                .trim();
    }
}
