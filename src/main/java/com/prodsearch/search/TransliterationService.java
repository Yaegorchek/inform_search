package com.prodsearch.search;

import java.util.HashMap;
import java.util.Map;

/**
 * Сервис для транслитерации с латиницы на кириллицу
 */
public class TransliterationService {
    private static final Map<String, String> translitMap = new HashMap<>();

    static {
        translitMap.put("a", "а");
        translitMap.put("b", "б");
        translitMap.put("v", "в");
        translitMap.put("g", "г");
        translitMap.put("d", "д");
        translitMap.put("e", "е");
        translitMap.put("yo", "ё");
        translitMap.put("zh", "ж");
        translitMap.put("z", "з");
        translitMap.put("i", "и");
        translitMap.put("y", "й");
        translitMap.put("k", "к");
        translitMap.put("l", "л");
        translitMap.put("m", "м");
        translitMap.put("n", "н");
        translitMap.put("o", "о");
        translitMap.put("p", "п");
        translitMap.put("r", "р");
        translitMap.put("s", "с");
        translitMap.put("t", "т");
        translitMap.put("u", "у");
        translitMap.put("f", "ф");
        translitMap.put("kh", "х");
        translitMap.put("ts", "ц");
        translitMap.put("ch", "ч");
        translitMap.put("sh", "ш");
        translitMap.put("shch", "щ");
        translitMap.put("''", "ъ");
        translitMap.put("y", "ы");
        translitMap.put("'", "ь");
        translitMap.put("e", "э");
        translitMap.put("yu", "ю");
        translitMap.put("ya", "я");
        translitMap.put("x", "кс");
        translitMap.put("w", "в");
        translitMap.put("q", "к");
        translitMap.put("ju", "ю");
        translitMap.put("ja", "я");
    }

    /**
     * Транслитерирует текст с латиницы на кириллицу
     */
    public String transliterate(String latinText) {
        if (latinText == null || latinText.isEmpty()) {
            return latinText;
        }

        String text = latinText.toLowerCase();
        StringBuilder result = new StringBuilder();
        int i = 0;

        while (i < text.length()) {
            boolean found = false;

            if (i + 3 < text.length()) {
                String fourChar = text.substring(i, i + 4);
                if (translitMap.containsKey(fourChar)) {
                    result.append(translitMap.get(fourChar));
                    i += 4;
                    found = true;
                    continue;
                }
            }

            if (i + 2 < text.length() && !found) {
                String threeChar = text.substring(i, i + 3);
                if (translitMap.containsKey(threeChar)) {
                    result.append(translitMap.get(threeChar));
                    i += 3;
                    found = true;
                    continue;
                }
            }

            if (i + 1 < text.length() && !found) {
                String twoChar = text.substring(i, i + 2);
                if (translitMap.containsKey(twoChar)) {
                    result.append(translitMap.get(twoChar));
                    i += 2;
                    found = true;
                    continue;
                }
            }

            if (!found) {
                String singleChar = text.substring(i, i + 1);
                if (translitMap.containsKey(singleChar)) {
                    result.append(translitMap.get(singleChar));
                } else {
                    result.append(singleChar);
                }
                i++;
            }
        }

        return result.toString();
    }

    /**
     * Определяет, нужно ли транслитерировать текст (содержит латинские буквы)
     */
    public boolean needsTransliteration(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        // Проверяем, содержит ли текст латинские буквы
        return text.matches(".*[a-zA-Z].*");
    }

    /**
     * Определяет, содержит ли текст кириллицу
     */
    public boolean containsCyrillic(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        return text.matches(".*[а-яА-ЯёЁ].*");
    }
}