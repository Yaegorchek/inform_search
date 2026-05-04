package com.prodsearch.detect;

import java.util.*;
import java.util.regex.*;

public final class ConnectingRodBearingInfoDetector {
    private static final Set<String> VEHICLE_BRANDS = Set.of(
            "TOYOTA", "NISSAN", "MITSUBISHI", "HONDA", "FORD", "MAZDA", "BMW", "MB", "MERCEDES", "AUDI", "VW", "VAG",
            "OPEL", "RENAULT", "PEUGEOT", "CITROEN", "FIAT", "IVECO", "VOLVO", "MAN", "SCANIA", "DAF", "ISUZU",
            "SUZUKI", "SUBARU", "HYUNDAI", "KIA", "DAEWOO", "CHEVROLET", "CHRYSLER", "LAND ROVER", "MINI", "LADA",
            "ВАЗ", "КАМАЗ", "МАЗ", "ЯМЗ", "ММЗ", "DEUTZ", "PERKINS", "CATERPILLAR", "CAT", "CUMMINS", "KOMATSU",
            "KUBOTA", "YANMAR", "XINCHAI", "JCB", "BOBCAT", "RVI", "SSANGYONG", "CHERY", "YUCHAI");

    private static final Set<String> BEARING_BRANDS = Set.of(
            "KING", "TAIHO", "KS", "MAHLE", "GLYCO", "MIBA", "UM", "KMP", "DAIDO", "NDC", "AE", "GOETZE", "FP-DIESEL",
            "CTP", "HUATAI", "ANAC", "MEC-DIESEL", "DIESELWAGNER", "AUTOWELT", "KSMG", "HAFFEN", "MAXIFORCE", "ETP",
            "AMP", "TDC", "SANICO");

    private static final Pattern QTY_PATTERN = Pattern.compile("(\\d{1,2})\\s*шт", Pattern.CASE_INSENSITIVE);
    private static final Pattern JOURNAL_PATTERN = Pattern.compile("на\\s+(\\d{1,2})\\s+ше", Pattern.CASE_INSENSITIVE);
    private static final Pattern CYL_PATTERN = Pattern.compile("(\\d{1,2})\\s*цил", Pattern.CASE_INSENSITIVE);
    private static final Pattern WIDTH_PATTERN = Pattern
            .compile("ширина\\s*[-=]?\\s*(\\d{1,3}(?:[.,]\\d+)?)\\s*(мм|mm)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ENGINE_MODEL_PATTERN = Pattern.compile(
            "\\b(\\d[A-ZА-Я]{1,5}[A-ZА-Я0-9.-]*|[A-ZА-Я]{1,6}\\d{1,4}[A-ZА-Я0-9.-]*)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern OEM_PATTERN = Pattern.compile("\\b[A-Z0-9-]{5,15}\\b", Pattern.CASE_INSENSITIVE);

    public static ConnectingRodBearingInfo detect(String title) {
        String normalized = title.toUpperCase().replace('Ё', 'Е');
        boolean detected = (normalized.contains("ВКЛАДЫШ") && normalized.contains("ШАТУН")) ||
                normalized.contains("ШАТУННЫЙ ВКЛАДЫШ") ||
                normalized.contains("ШАТУННЫЕ ВКЛАДЫШИ");

        String vehicleBrand = findBrand(normalized, VEHICLE_BRANDS);
        String bearingBrand = findBrand(normalized, BEARING_BRANDS);

        String repairSize = parseRepairSize(normalized);
        String kitType = "UNKNOWN"; // Detailed logic skipped for brevity
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

    private static String findBrand(String text, Set<String> brands) {
        for (String b : brands) {
            if (text.contains(b))
                return b;
        }
        return null;
    }

    private static String parseRepairSize(String text) {
        if (text.contains("STD") || text.contains("СТД") || text.contains("СТАНДАРТ") || text.contains("НОМИНАЛ"))
            return "STD";
        if (text.contains("0.25") || text.contains("0,25") || text.contains("1 РЕМОНТ") || text.contains("1-Й РЕМОНТ")
                || text.contains("ПЕРВЫЙ РЕМОНТ"))
            return "0.25";
        if (text.contains("0.50") || text.contains("0,50") || text.contains("0.5") || text.contains("0,5")
                || text.contains("2 РЕМОНТ"))
            return "0.50";
        if (text.contains("0.75") || text.contains("0,75") || text.contains("3 РЕМОНТ"))
            return "0.75";
        if (text.contains("1.00") || text.contains("1,00"))
            return "1.00";
        return null;
    }

    private static Integer extractInt(Pattern p, String t) {
        Matcher m = p.matcher(t);
        return m.find() ? Integer.valueOf(m.group(1)) : null;
    }

    private static Double extractDouble(Pattern p, String t) {
        Matcher m = p.matcher(t);
        return m.find() ? Double.valueOf(m.group(1).replace(',', '.')) : null;
    }

    private static List<String> extractEngineModels(String text, String vb, String bb) {
        List<String> models = new ArrayList<>();
        Matcher m = ENGINE_MODEL_PATTERN.matcher(text);
        Set<String> ignore = Set.of("STD", "OEM", "12V", "16V", "24V", "V6", "V8", "V12", "D52MM", "D50MM", "8ШТ",
                "12ШТ", "0.25", "0.50", "0.75", "1.00", "EURO", "ORIGINAL", "PREMIUM", "РАСПРОДАЖА");
        while (m.find()) {
            String match = m.group(1);
            if (ignore.contains(match))
                continue;
            if (match.equals(vb) || match.equals(bb))
                continue;
            if (match.matches("\\d+"))
                continue; // ignore numbers
            models.add(match);
        }
        return models;
    }

    private static Set<String> extractFeatures(String text) {
        Set<String> features = new HashSet<>();
        if (text.contains("SPUTTER"))
            features.add("SPUTTER");
        if (text.contains("UPPER"))
            features.add("UPPER");
        if (text.contains("LOWER"))
            features.add("LOWER");
        return features;
    }

    private static List<String> extractOemCodes(String text) {
        List<String> codes = new ArrayList<>();
        Matcher m = OEM_PATTERN.matcher(text);
        while (m.find()) {
            codes.add(m.group());
        }
        return codes;
    }
}
