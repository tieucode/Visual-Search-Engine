package com.visualsearch.util;

import java.text.Normalizer;
import java.util.Locale;

public final class TextNormalizer {

    private TextNormalizer() {
    }

    public static String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String normalized = text.toLowerCase(Locale.ROOT)
                .replace('đ', 'd');

        normalized = Normalizer.normalize(
                normalized,
                Normalizer.Form.NFD
        );

        normalized = normalized.replaceAll("\\p{M}+", "");

        // Chuẩn hóa khoảng trắng
        return normalized.trim().replaceAll("\\s+", " ");
    }
}