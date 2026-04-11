package com.prodsearch.search;

import com.ibm.icu.text.Transliterator;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ArticleExtractor {

    // Паттерн: ищем комбинации латиницы и цифр с возможными разделителями (- . /)
    private static final Pattern CODE_PATTERN = Pattern.compile("(?i)\\b[A-Z0-9][A-Z0-9\\-./]{2,18}[A-Z0-9]\\b");

    private static final Transliterator cyrToLat = Transliterator.getInstance("Cyrillic-Latin");

    public static Set<String> extract(String text) {
        if (text == null || text.isEmpty()) return Collections.emptySet();

        Set<String> foundCodes = new HashSet<>();
        Matcher matcher = CODE_PATTERN.matcher(text);

        while (matcher.find()) {
            String candidate = matcher.group();

            if (candidate.matches(".*\\d.*")) {
                foundCodes.add(normalize(candidate));
            }
        }
        return foundCodes;
    }

    public static String normalize(String input) {
        if (input == null) return "";

        String lat = cyrToLat.transliterate(input.toLowerCase());

        return lat.replaceAll("[^a-z0-9]", "");
    }
}