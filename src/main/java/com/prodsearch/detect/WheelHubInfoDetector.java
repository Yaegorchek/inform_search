package com.prodsearch.detect;

import java.util.*;
import java.util.regex.*;

public final class WheelHubInfoDetector {
    private static final Set<String> BRANDS = Set.of(
            "BPW", "SAF", "ROR", "RVI", "MAN", "MERCEDES", "MB", "VOLVO", "SCANIA", "IVECO", "DAF", "TOYOTA", "NISSAN",
            "HYUNDAI", "KIA", "FORD", "RENAULT", "BMW", "AUDI", "VW", "MAZDA", "HONDA", "CITROEN", "PEUGEOT", "PORSCHE",
            "LEXUS", "SSANGYONG", "CHERY", "LIFAN", "GAZ", "ГАЗ", "ПАЗ", "ЛИАЗ", "МАЗ", "КАМАЗ", "УРАЛ", "AMP", "TVH",
            "GMB", "SKF", "NTN", "SNR", "FEBEST", "STELLOX", "SAT", "TRUCKTEC", "CARRARO", "HIDROMEK");

    private static final Pattern THREAD_PATTERN = Pattern.compile("M\\d{2,3}x\\d(?:[.,]\\d)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BOLT_PATTERN = Pattern.compile("\\d{1,2}x[ØOО]?\\d{2,3}", Pattern.CASE_INSENSITIVE);
    private static final Pattern OEM_PATTERN = Pattern.compile("\\b[A-Z0-9-]{5,15}\\b", Pattern.CASE_INSENSITIVE);

    public static WheelHubInfo detect(String title) {
        String normalized = title.toUpperCase().replace('Ё', 'Е');
        boolean detected = normalized.contains("СТУПИЦ");

        String hubKind = "WHEEL_HUB"; // Simplification
        String brand = findBrand(normalized, BRANDS);
        String position = "UNKNOWN";
        String side = "UNKNOWN";
        String bearingState = "UNKNOWN";
        boolean assembly = false;
        boolean abs = normalized.contains("ABS") || normalized.contains("АБС");
        String threadSize = extractPattern(THREAD_PATTERN, title);
        String boltPattern = extractPattern(BOLT_PATTERN, title);
        String geometry = null;
        String tonnage = null;
        String series = null;
        List<String> oemCodes = extractOemCodes(normalized);

        return new WheelHubInfo(detected, hubKind, brand, position, side, bearingState, assembly, abs, threadSize,
                boltPattern, geometry, tonnage, series, oemCodes);
    }

    private static String findBrand(String text, Set<String> brands) {
        for (String b : brands) {
            if (text.contains(b))
                return b;
        }
        return null;
    }

    private static String extractPattern(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? m.group() : null;
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
