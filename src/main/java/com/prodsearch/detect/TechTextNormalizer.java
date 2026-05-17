package com.prodsearch.detect;

import java.util.Locale;
import java.util.Map;

public final class TechTextNormalizer {
    private static final Map<Character, Character> CONFUSABLES = Map.ofEntries(
            Map.entry('А', 'A'),
            Map.entry('В', 'B'),
            Map.entry('Е', 'E'),
            Map.entry('К', 'K'),
            Map.entry('М', 'M'),
            Map.entry('Н', 'H'),
            Map.entry('О', 'O'),
            Map.entry('Р', 'P'),
            Map.entry('С', 'C'),
            Map.entry('Т', 'T'),
            Map.entry('У', 'Y'),
            Map.entry('Х', 'X'));

    private TechTextNormalizer() {
    }

    public static String normalizeForScanning(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String base = baseNormalize(raw);
        String cleaned = replaceInvalidWithSpace(base, true, true);
        String collapsed = collapseSpaces(cleaned).trim();
        if (collapsed.isEmpty()) {
            return "";
        }

        String[] tokens = collapsed.split("\\s+");
        StringBuilder out = new StringBuilder(collapsed.length());
        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            out.append(normalizeTokenForCodes(token)).append(' ');
        }
        return out.toString().trim();
    }

    public static String normalizeThread(String raw) {
        return normalizeCodeToken(raw, true, true);
    }

    public static String normalizeBoltPattern(String raw) {
        String normalized = normalizeCodeToken(raw, true, true);
        if (normalized == null) {
            return null;
        }
        return normalized.replace("Ø", "").replace("O", "");
    }

    public static String normalizeGeometry(String raw) {
        String normalized = normalizeCodeToken(raw, true, true);
        if (normalized == null) {
            return null;
        }
        return normalized.replace("Ø", "D");
    }

    public static String normalizeTonnage(String raw) {
        return normalizeCodeToken(raw, true, true);
    }

    public static String normalizeEngineModel(String raw) {
        return normalizeCodeToken(raw, true, true);
    }

    public static String normalizeOemCode(String raw) {
        return normalizeCodeToken(raw, false, false);
    }

    public static String normalizeCodeToken(String raw, boolean keepSeparators, boolean keepDiameterSymbol) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String base = baseNormalize(raw);
        String filtered = replaceInvalidWithSpace(base, keepSeparators, keepDiameterSymbol);
        String collapsed = collapseSpaces(filtered).trim();
        if (collapsed.isEmpty()) {
            return null;
        }
        String token = collapsed.replace(" ", "");
        return normalizeTokenForCodes(token);
    }

    private static String baseNormalize(String raw) {
        String normalized = raw.toUpperCase(Locale.ROOT).replace('Ё', 'Е');
        normalized = normalized.replace(',', '.');
        normalized = normalized.replace('–', '-').replace('—', '-');
        normalized = normalized.replace('×', 'X');
        return normalized;
    }

    private static String replaceInvalidWithSpace(String text, boolean keepSeparators, boolean keepDiameterSymbol) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                out.append(c);
                continue;
            }
            if (c == 'Ø' && keepDiameterSymbol) {
                out.append(c);
                continue;
            }
            if (keepSeparators && (c == '-' || c == '.' || c == '/' || c == '+')) {
                out.append(c);
                continue;
            }
            if (Character.isWhitespace(c)) {
                out.append(' ');
            } else {
                out.append(' ');
            }
        }
        return out.toString();
    }

    private static String normalizeTokenForCodes(String token) {
        if (token == null || token.isBlank()) {
            return token;
        }
        boolean hasDigit = false;
        boolean hasLatin = false;
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (Character.isDigit(c)) {
                hasDigit = true;
            } else if (c >= 'A' && c <= 'Z') {
                hasLatin = true;
            }
            if (hasDigit && hasLatin) {
                break;
            }
        }

        boolean normalizeConfusables = hasDigit || hasLatin;
        if (!normalizeConfusables) {
            return token;
        }

        StringBuilder out = new StringBuilder(token.length());
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            Character mapped = CONFUSABLES.get(c);
            out.append(mapped != null ? mapped : c);
        }
        return out.toString();
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
