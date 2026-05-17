package com.prodsearch.detect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConnectingRodBearingInfoDetector {
    private static final Set<String> VEHICLE_BRANDS = Set.of(
            "TOYOTA", "NISSAN", "MITSUBISHI", "HONDA", "FORD", "MAZDA", "BMW", "MB", "MERCEDES", "AUDI", "VW",
            "VAG", "OPEL", "RENAULT", "PEUGEOT", "CITROEN", "FIAT", "IVECO", "VOLVO", "MAN", "SCANIA", "DAF",
            "ISUZU", "SUZUKI", "SUBARU", "HYUNDAI", "KIA", "DAEWOO", "CHEVROLET", "CHRYSLER", "LAND ROVER",
            "MINI", "LADA", "ВАЗ", "КАМАЗ", "МАЗ", "ЯМЗ", "ММЗ", "DEUTZ", "PERKINS", "CATERPILLAR", "CAT",
            "CUMMINS", "KOMATSU", "KUBOTA", "YANMAR", "XINCHAI", "JCB", "BOBCAT", "RVI", "SSANGYONG", "CHERY",
            "YUCHAI");

    private static final Set<String> BEARING_BRANDS = Set.of(
            "KING", "TAIHO", "KS", "MAHLE", "GLYCO", "MIBA", "UM", "KMP", "DAIDO", "NDC", "AE", "GOETZE",
            "FP-DIESEL", "CTP", "HUATAI", "ANAC", "MEC-DIESEL", "DIESELWAGNER", "AUTOWELT", "KSMG", "HAFFEN",
            "MAXIFORCE", "ETP", "AMP", "TDC", "SANICO");

    private static final Pattern QTY_PATTERN = Pattern.compile("\\b(\\d{1,2})\\s*шт\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern JOURNAL_PATTERN = Pattern.compile("\\bна\\s+(\\d{1,2})\\s+ше",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CYL_PATTERN = Pattern.compile("\\b(\\d{1,2})\\s*цил", Pattern.CASE_INSENSITIVE);
    private static final Pattern WIDTH_PATTERN = Pattern.compile(
            "\\bширин\\w*\\s*[-=]?\\s*(\\d{1,3}(?:[.,]\\d+)?)\\s*(мм|mm)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern REPAIR_SIZE_PATTERN = Pattern
            .compile("(?<!\\d)(?:\\+|-)?(0[.,]\\d{2,3}|1[.,]00|0[.,]5)(?!\\d)");

    private static final List<Pattern> ENGINE_MODEL_PATTERNS = List.of(
            Pattern.compile("\\b\\d[A-ZА-Я]{1,4}[0-9A-ZА-Я.-]*\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b[A-ZА-Я]{1,4}\\d{1,4}[A-ZА-Я0-9.-]*\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b[A-ZА-Я]{1,3}\\d{1,2}[A-ZА-Я]{1,3}\\b", Pattern.CASE_INSENSITIVE));

    private static final List<Pattern> OEM_PATTERNS = List.of(
            Pattern.compile("\\b[A-Z0-9]{2,6}[-/ ][A-Z0-9]{2,6}[-/ ][A-Z0-9]{1,6}\\b",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b\\d{5,12}\\b"),
            Pattern.compile("\\b\\d{2,4}/\\d{4,6}\\b"));

    private static final Map<String, List<String>> FEATURE_MARKERS = Map.ofEntries(
            Map.entry("SPUTTER", List.of("SPUTTER", "СПУТТЕР")),
            Map.entry("POLYMER_COATING", List.of("ПОЛИМЕР")),
            Map.entry("WITHOUT_LOCKS", List.of("БЕЗ ЗАМК")),
            Map.entry("WITH_HOLE", List.of("С ОТВЕРСТ", "С ОТВ")),
            Map.entry("WITHOUT_HOLE", List.of("БЕЗ ОТВЕРСТ")),
            Map.entry("UPPER", List.of("ВЕРХ", "ВЕРХН")),
            Map.entry("LOWER", List.of("НИЖН", "НИЗ")),
            Map.entry("RACING", List.of("ГОНОЧН", "RACING")),
            Map.entry("REINFORCED", List.of("УСИЛЕНН", "УПРОЧН")));

    private record KitRule(String type, List<Pattern> patterns) {
    }

    private static final List<KitRule> KIT_RULES = List.of(
            new KitRule("ENGINE_KIT",
                    List.of(Pattern.compile("НА\\s*ДВИГАТЕЛ", Pattern.CASE_INSENSITIVE),
                            Pattern.compile("НА\\s*ДВС", Pattern.CASE_INSENSITIVE),
                            Pattern.compile("НА\\s*МОТОР", Pattern.CASE_INSENSITIVE))),
            new KitRule("ONE_JOURNAL", List.of(Pattern.compile("НА\\s*1\\s*ШЕЙК", Pattern.CASE_INSENSITIVE))),
            new KitRule("PAIR",
                    List.of(Pattern.compile("\\bПАРА\\b", Pattern.CASE_INSENSITIVE),
                            Pattern.compile("\\b2\\s*ШТ\\b", Pattern.CASE_INSENSITIVE))),
            new KitRule("KIT",
                    List.of(Pattern.compile("\\bК[-/\\s]?Т\\b", Pattern.CASE_INSENSITIVE),
                            Pattern.compile("\\bК[-/\\s]?КТ\\b", Pattern.CASE_INSENSITIVE),
                            Pattern.compile("КОМПЛ", Pattern.CASE_INSENSITIVE),
                            Pattern.compile("КОМПЛЕКТ", Pattern.CASE_INSENSITIVE))));

    private static final Map<String, String> REPAIR_WORD_MAP = Map.ofEntries(
            Map.entry("1 РЕМОНТ", "0.25"),
            Map.entry("1-Й РЕМОНТ", "0.25"),
            Map.entry("ПЕРВЫЙ РЕМОНТ", "0.25"),
            Map.entry("2 РЕМОНТ", "0.50"),
            Map.entry("2-Й РЕМОНТ", "0.50"),
            Map.entry("ВТОРОЙ РЕМОНТ", "0.50"),
            Map.entry("3 РЕМОНТ", "0.75"),
            Map.entry("3-Й РЕМОНТ", "0.75"),
            Map.entry("ТРЕТИЙ РЕМОНТ", "0.75"));

    private static final Set<String> ENGINE_IGNORE = Set.of(
            "STD", "СТД", "OEM", "ORIGINAL", "PREMIUM", "EURO", "V6", "V8", "V12", "12V", "16V", "24V",
            "D52MM", "D50MM", "D48MM", "D05", "MM", "РАСПРОДАЖА");

    public static ConnectingRodBearingInfo detect(String title) {
        String normalized = TechTextNormalizer.normalizeForScanning(title);
        List<String> tokens = tokenize(normalized);

        boolean detected = hasTokenPrefix(tokens, "ВКЛАДЫШ") && hasTokenPrefix(tokens, "ШАТУН");

        String vehicleBrand = findKnownPhrase(normalized, VEHICLE_BRANDS);
        String bearingBrand = findKnownPhrase(normalized, BEARING_BRANDS);

        String repairSize = parseRepairSize(normalized);
        String kitType = detectKitType(normalized);
        Integer quantity = extractInt(QTY_PATTERN, title);
        Integer journalCount = extractInt(JOURNAL_PATTERN, title);
        Integer cylinderCount = extractInt(CYL_PATTERN, title);
        Double widthMm = extractDouble(WIDTH_PATTERN, title);

        List<String> engineModels = extractEngineModels(normalized, vehicleBrand, bearingBrand);
        Set<String> features = extractFeatures(normalized);
        List<String> oemCodes = extractOemCodes(normalized);

        return new ConnectingRodBearingInfo(detected, vehicleBrand, bearingBrand, repairSize, kitType, quantity,
                journalCount, cylinderCount, widthMm, engineModels, features, oemCodes);
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

    private static String parseRepairSize(String text) {
        if (text.contains("STD") || text.contains("СТД") || text.contains("СТАНДАРТ") || text.contains("НОМИНАЛ")) {
            return "STD";
        }

        Matcher sizeMatcher = REPAIR_SIZE_PATTERN.matcher(text);
        if (sizeMatcher.find()) {
            String size = sizeMatcher.group(1).replace(',', '.');
            if ("0.5".equals(size)) {
                size = "0.50";
            }
            if (size.startsWith("0.")) {
                return size;
            }
            if (size.startsWith("1.")) {
                return size;
            }
        }

        for (Map.Entry<String, String> entry : REPAIR_WORD_MAP.entrySet()) {
            if (text.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return null;
    }

    private static String detectKitType(String text) {
        for (KitRule rule : KIT_RULES) {
            for (Pattern pattern : rule.patterns()) {
                if (pattern.matcher(text).find()) {
                    return rule.type();
                }
            }
        }
        return null;
    }

    private static Integer extractInt(Pattern p, String t) {
        if (t == null) {
            return null;
        }
        Matcher m = p.matcher(t);
        return m.find() ? Integer.valueOf(m.group(1)) : null;
    }

    private static Double extractDouble(Pattern p, String t) {
        if (t == null) {
            return null;
        }
        Matcher m = p.matcher(t);
        return m.find() ? Double.valueOf(m.group(1).replace(',', '.')) : null;
    }

    private static List<String> extractEngineModels(String text, String vehicleBrand, String bearingBrand) {
        LinkedHashSet<String> models = new LinkedHashSet<>();
        for (Pattern pattern : ENGINE_MODEL_PATTERNS) {
            Matcher m = pattern.matcher(text);
            while (m.find()) {
                String match = m.group();
                if (match == null || match.isBlank()) {
                    continue;
                }
                if (ENGINE_IGNORE.contains(match)) {
                    continue;
                }
                if (match.equals(vehicleBrand) || match.equals(bearingBrand)) {
                    continue;
                }
                if (match.matches("\\d+")) {
                    continue;
                }
                if (match.matches("[+-]?\\d+[.,]\\d+")) {
                    continue;
                }
                String normalized = TechTextNormalizer.normalizeEngineModel(match);
                if (normalized != null && !normalized.isBlank()) {
                    models.add(normalized);
                }
            }
        }
        return new ArrayList<>(models);
    }

    private static Set<String> extractFeatures(String text) {
        LinkedHashSet<String> features = new LinkedHashSet<>();
        for (Map.Entry<String, List<String>> entry : FEATURE_MARKERS.entrySet()) {
            for (String marker : entry.getValue()) {
                if (text.contains(marker)) {
                    features.add(entry.getKey());
                    break;
                }
            }
        }
        return features;
    }

    private static List<String> extractOemCodes(String text) {
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (Pattern pattern : OEM_PATTERNS) {
            Matcher m = pattern.matcher(text);
            while (m.find()) {
                String code = m.group();
                if (code == null) {
                    continue;
                }
                code = code.trim();
                if (code.length() < 5) {
                    continue;
                }
                if (code.matches("[+-]?\\d+[.,]\\d+")) {
                    continue;
                }
                if (code.equals("000") || code.equals("025") || code.equals("050") || code.equals("075")) {
                    continue;
                }
                String normalized = TechTextNormalizer.normalizeOemCode(code);
                if (normalized != null && !normalized.isBlank()) {
                    codes.add(normalized);
                }
            }
        }
        return new ArrayList<>(codes);
    }
}
