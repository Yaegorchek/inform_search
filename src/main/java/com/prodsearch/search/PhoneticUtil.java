package com.prodsearch.search;

import com.ibm.icu.text.Transliterator;
import org.apache.commons.codec.language.bm.BeiderMorseEncoder;
import org.apache.commons.codec.language.bm.NameType;
import org.apache.commons.codec.language.bm.RuleType;

public class PhoneticUtil {

    private static final BeiderMorseEncoder encoder = new BeiderMorseEncoder();
    private static final Transliterator cyrToLat = Transliterator.getInstance(
            "Cyrillic-Latin; Any-Latin; NFD; [:Nonspacing Mark:] Remove; NFC"
    );

    static {
        encoder.setNameType(NameType.GENERIC);
        encoder.setRuleType(RuleType.APPROX);
        encoder.setConcat(false);
    }

    /** Преобразует текст в фонетическую форму, сначала транслитерируя кириллицу */
    public static String toPhonetic(String input) {
        if (input == null || input.isEmpty()) return "";

        try {
            String translit = cyrToLat.transliterate(input);
            System.out.println("ICU Result: " + translit);
            // нормализация: только буквы и цифры, нижний регистр
            String normalized = translit.toLowerCase()
                    .replaceAll("[^\\p{L}\\p{Nd}]+", " ")
                    .trim();

            String encoded = encoder.encode(normalized);
            System.out.println(encoded);
            return encoded != null ? encoded : "";
        } catch (Exception e) {
            System.err.println("Phonetic encoding failed for: " + input + " -> " + e.getMessage());
            return "";
        }
    }
}
