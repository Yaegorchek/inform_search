package com.prodsearch.detect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WheelHubInfoDetector {
    private static final Set<String> BRANDS = Set.of(
            "BPW", "SAF", "ROR", "RVI", "MAN", "MERCEDES", "MB", "VOLVO", "SCANIA", "IVECO", "DAF", "TOYOTA",
            "NISSAN", "HYUNDAI", "KIA", "FORD", "RENAULT", "BMW", "AUDI", "VW", "MAZDA", "HONDA", "CITROEN",
            "PEUGEOT", "PORSCHE", "LEXUS", "SSANGYONG", "CHERY", "LIFAN", "GAZ", "ГАЗ", "ПАЗ", "ЛИАЗ", "МАЗ",
            "КАМАЗ", "УРАЛ", "AMP", "TVH", "GMB", "SKF", "NTN", "SNR", "FEBEST", "STELLOX", "SAT",
            "TRUCKTEC", "CARRARO", "HIDROMEK");

    private static final Pattern THREAD_PATTERN = Pattern.compile("\\bM\\d{2,3}[XХ×]\\d(?:[.,]\\d)?\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BOLT_PATTERN = Pattern.compile("\\b\\d{1,2}[XХ×][ØOО]?\\d{2,3}\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern GEOMETRY_PATTERN = Pattern.compile(
            "(?:[ØD]\\s*=?\\s*\\d{2,4}(?:[.,]\\d+)?(?:/(?:[ØD]?\\s*\\d{2,4}(?:[.,]\\d+)?))*)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TONNAGE_PATTERN = Pattern.compile(
            "\\b\\d+(?:[.,]\\d+)?\\s*(?:-|–|—)\\s*\\d+(?:[.,]\\d+)?\\s*[TТ]\\b|\\b\\d+(?:[.,]\\d+)?\\s*[TТ]\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern OEM_PATTERN = Pattern.compile("\\b[A-Z0-9]{5,15}\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ASSEMBLY_PATTERN = Pattern.compile("\\bВ\\s*СБ\\b|\\bВ\\s*СБОРЕ\\b|\\bСБОРЕ\\b",
            Pattern.CASE_INSENSITIVE);

    private static final List<String> SERIES_TOKENS = List.of(
            "ECO PLUS", "ECO MAXX", "ECO", "INTEGRAL", "SKRB", "SKHS", "SKHZ");

    public static WheelHubInfo detect(String title) {
        String normalized = TechTextNormalizer.normalizeForScanning(title);
        List<String> tokens = tokenize(normalized);
        boolean detected = hasTokenPrefix(tokens, "СТУПИЦ");

        String hubKind = detectHubKind(normalized);
        String brand = findKnownPhrase(normalized, BRANDS);
        String position = detectPosition(tokens);
        String side = detectSide(tokens);
        String bearingState = detectBearingState(normalized);
        Boolean assembly = detectAssembly(normalized);
        Boolean abs = detectAbs(normalized);
        String threadSize = TechTextNormalizer.normalizeThread(extractPattern(THREAD_PATTERN, normalized));
        String boltPattern = TechTextNormalizer.normalizeBoltPattern(extractPattern(BOLT_PATTERN, normalized));
        String geometry = TechTextNormalizer.normalizeGeometry(extractPattern(GEOMETRY_PATTERN, normalized));
        String tonnage = TechTextNormalizer.normalizeTonnage(extractPattern(TONNAGE_PATTERN, normalized));
        String series = detectSeries(normalized);
        List<String> oemCodes = extractOemCodes(normalized);

        return new WheelHubInfo(detected, hubKind, brand, position, side, bearingState, assembly, abs, threadSize,
                boltPattern, geometry, tonnage, series, oemCodes);
    }

    private static List<String> tokenize(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return List.of();
        }
        return Arrays.asList(normalized.split("[^A-ZА-Я0-9]+"));
    }

    private static boolean hasTokenPrefix(List<String> tokens, String prefix) {
        for (String token : tokens) {
            if (token.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String findKnownPhrase(String text, Set<String> phrases) {
        List<String> sorted = new ArrayList<>(phrases);
        sorted.sort(Comparator.comparingInt(String::length).reversed());
        for (String phrase : sorted) {
            String pattern = "(?<![A-ZА-Я0-9])" + Pattern.quote(phrase) + "(?![A-ZА-Я0-9])";
            if (Pattern.compile(pattern).matcher(text).find()) {
                return phrase;
            }
        }
        return null;
    }

    private static String detectHubKind(String text) {
        if (text.contains("КОЛЕС")) {
            return "WHEEL";
        }
        if (text.contains("ВЕНТИЛЯТОР")) {
            return "FAN";
        }
        if (text.contains("СИНХРОНИЗАТОР")) {
            return "SYNCHRONIZER";
        }
        if (text.contains("РЕДУКТОР")) {
            return "REDUCER";
        }
        if (text.contains("ТРАНСМИСС")) {
            return "TRANSMISSION";
        }
        if (text.contains("МУФТ")) {
            return "CLUTCH";
        }
        if (text.contains("ТУРБИН")) {
            return "TURBINE";
        }
        if (text.contains("ШКИВ")) {
            return "PULLEY";
        }
        if (text.contains("КРЫЛЬЧАТ")) {
            return "IMPELLER";
        }
        if (text.contains("ПРИВОД")) {
            return "DRIVE";
        }
        return null;
    }

    private static String detectPosition(List<String> tokens) {
        for (String token : tokens) {
            if (token.startsWith("ПЕРЕД")) {
                return "FRONT";
            }
            if (token.startsWith("ЗАД")) {
                return "REAR";
            }
        }
        return null;
    }

    private static String detectSide(List<String> tokens) {
        for (String token : tokens) {
            if (token.startsWith("ЛЕВ")) {
                return "LEFT";
            }
            if (token.startsWith("ПРАВ")) {
                return "RIGHT";
            }
        }
        return null;
    }

    private static String detectBearingState(String text) {
        if (text.contains("БЕЗ ПОДШ")) {
            return "WITHOUT_BEARINGS";
        }
        if (text.contains("С ПОДШ") || text.contains("ПОДШИПНИК")) {
            return "WITH_BEARINGS";
        }
        return null;
    }

    private static Boolean detectAssembly(String text) {
        if (ASSEMBLY_PATTERN.matcher(text).find()) {
            return Boolean.TRUE;
        }
        return null;
    }

    private static Boolean detectAbs(String text) {
        if (text.contains("БЕЗ ABS") || text.contains("БЕЗ АБС")) {
            return Boolean.FALSE;
        }
        if (text.contains("ABS") || text.contains("АБС")) {
            return Boolean.TRUE;
        }
        return null;
    }

    private static String detectSeries(String text) {
        for (String token : SERIES_TOKENS) {
            if (text.contains(token)) {
                return token;
            }
        }
        return null;
    }

    private static String extractPattern(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? m.group() : null;
    }

    private static List<String> extractOemCodes(String text) {
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        Matcher m = OEM_PATTERN.matcher(text);
        while (m.find()) {
            String code = m.group();
            if (code.length() < 5) {
                continue;
            }
            String normalized = TechTextNormalizer.normalizeOemCode(code);
            if (normalized != null && !normalized.isBlank()) {
                codes.add(normalized);
            }
        }
        return new ArrayList<>(codes);
    }
}
