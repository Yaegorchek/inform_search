package com.prodsearch.search.article;

import com.ibm.icu.text.Transliterator;

import java.util.Locale;

/**
 * Deterministic text normalization helpers for article extraction.
 *
 * Rules:
 * - transliterate Cyrillic to Latin
 * - lowercase
 * - keep only [a-z0-9] for normalized article form
 * - build structural masks where:
 * L = letter, D = digit, separators are kept as-is
 */
public final class ArticleTextUtil {

    private static final Transliterator CYR_TO_LAT = Transliterator.getInstance(
            "Any-Latin; NFD; [:Nonspacing Mark:] Remove; NFC");

    private ArticleTextUtil() {
    }

    public static CharKind classify(char c) {
        if (Character.isLetter(c)) {
            return CharKind.LETTER;
        }
        if (Character.isDigit(c)) {
            return CharKind.DIGIT;
        }
        if (isSeparator(c)) {
            return CharKind.SEPARATOR;
        }
        if (Character.isWhitespace(c)) {
            return CharKind.SPACE;
        }
        return CharKind.OTHER;
    }

    public static boolean isSeparator(char c) {
        return c == '-' || c == '.' || c == '/';
    }

    /**
     * Stable normalization for article comparison/indexing.
     * Returns only lowercase latin letters and digits.
     */
    public static String normalizeArticle(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }

        String transliterated = transliterateToLatin(raw).toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(transliterated.length());

        for (int i = 0; i < transliterated.length(); i++) {
            char c = transliterated.charAt(i);
            if (c >= 'a' && c <= 'z') {
                out.append(c);
            } else if (c >= '0' && c <= '9') {
                out.append(c);
            }
        }

        return out.toString();
    }

    /**
     * Normalization used before tokenization/scanning.
     * Keeps letters, digits, spaces and known separators.
     * All other chars become spaces to avoid accidental concatenation.
     */
    public static String normalizeForScanning(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }

        String transliterated = transliterateToLatin(raw).toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(transliterated.length());

        for (int i = 0; i < transliterated.length(); i++) {
            char c = transliterated.charAt(i);
            CharKind kind = classify(c);

            switch (kind) {
                case LETTER, DIGIT -> out.append(c);
                case SEPARATOR, SPACE -> out.append(c);
                default -> out.append(' ');
            }
        }

        return collapseSpaces(out.toString()).trim();
    }

    /**
     * Builds structural mask from raw candidate.
     * Example:
     * - "0281-002-241" -> "DDDD-DDD-DDD"
     * - "W213" -> "LDDD"
     * - "A-1234" -> "L-DDDD"
     *
     * Spaces and unsupported symbols are ignored in mask.
     */
    public static String buildMask(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }

        String normalized = normalizeForScanning(raw);
        StringBuilder mask = new StringBuilder(normalized.length());

        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            CharKind kind = classify(c);

            if (kind == CharKind.LETTER) {
                mask.append('L');
            } else if (kind == CharKind.DIGIT) {
                mask.append('D');
            } else if (kind == CharKind.SEPARATOR) {
                if (mask.length() > 0 && mask.charAt(mask.length() - 1) != c) {
                    mask.append(c);
                } else if (mask.length() > 0 && mask.charAt(mask.length() - 1) == c) {
                    // skip duplicated separator in mask
                } else {
                    // skip leading separator
                }
            }
        }

        while (mask.length() > 0 && isSeparator(mask.charAt(mask.length() - 1))) {
            mask.deleteCharAt(mask.length() - 1);
        }

        return mask.toString();
    }

    public static boolean hasLetter(String s) {
        if (s == null || s.isBlank()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (classify(s.charAt(i)) == CharKind.LETTER) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasDigit(String s) {
        if (s == null || s.isBlank()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (classify(s.charAt(i)) == CharKind.DIGIT) {
                return true;
            }
        }
        return false;
    }

    public static boolean isDigitsOnly(String s) {
        if (s == null || s.isBlank()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (classify(s.charAt(i)) != CharKind.DIGIT) {
                return false;
            }
        }
        return true;
    }

    private static String transliterateToLatin(String text) {
        return CYR_TO_LAT.transliterate(text);
    }

    private static String collapseSpaces(String s) {
        StringBuilder out = new StringBuilder(s.length());
        boolean prevSpace = false;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                if (!prevSpace) {
                    out.append(' ');
                    prevSpace = true;
                }
            } else {
                out.append(c);
                prevSpace = false;
            }
        }

        return out.toString();
    }
}
