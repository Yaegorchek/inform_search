package com.prodsearch.search.article;

import java.util.Optional;

/**
 * DFA for numeric article recognition.
 *
 * Recognizes:
 * - NUMERIC: digits only (e.g. 0281002241, 123456)
 * - NUMERIC_COMPOSITE: digit groups separated by -, ., / (e.g. 0281-002-241,
 * 12/345/67, 10.20.30)
 *
 * Rules:
 * - must start with a digit
 * - separators allowed only between digit groups
 * - separators cannot be doubled
 * - must end with a digit
 */
public class NumericArticleAutomaton implements ArticleAutomaton {

    private static final int MIN_DIGITS = 3;

    /**
     * DFA states:
     * START - before reading any accepted symbol
     * IN_DIGITS - currently reading a digit group
     * AFTER_SEPARATOR - separator consumed, expecting next digit group
     * REJECT - invalid sequence
     */
    private enum State {
        START,
        IN_DIGITS,
        AFTER_SEPARATOR,
        REJECT
    }

    @Override
    public Optional<ArticleMatch> tryMatch(String candidate) {
        if (candidate == null) {
            return Optional.empty();
        }

        String trimmed = candidate.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }

        State state = State.START;
        int digitCount = 0;
        int separatorCount = 0;

        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            CharKind kind = ArticleTextUtil.classify(ch);

            switch (state) {
                case START -> {
                    if (kind == CharKind.DIGIT) {
                        state = State.IN_DIGITS;
                        digitCount++;
                    } else {
                        state = State.REJECT;
                    }
                }
                case IN_DIGITS -> {
                    if (kind == CharKind.DIGIT) {
                        digitCount++;
                    } else if (kind == CharKind.SEPARATOR) {
                        state = State.AFTER_SEPARATOR;
                        separatorCount++;
                    } else {
                        state = State.REJECT;
                    }
                }
                case AFTER_SEPARATOR -> {
                    if (kind == CharKind.DIGIT) {
                        state = State.IN_DIGITS;
                        digitCount++;
                    } else {
                        state = State.REJECT;
                    }
                }
                case REJECT -> {
                    // early stop once invalid
                    return Optional.empty();
                }
            }

            if (state == State.REJECT) {
                return Optional.empty();
            }
        }

        // Must finish inside digit group (i.e., last char is digit), and be meaningful
        if (state != State.IN_DIGITS || digitCount < MIN_DIGITS) {
            return Optional.empty();
        }

        String normalized = ArticleTextUtil.normalizeArticle(trimmed);
        if (normalized.isEmpty() || !ArticleTextUtil.isDigitsOnly(normalized)) {
            return Optional.empty();
        }

        ArticleType type = separatorCount > 0 ? ArticleType.NUMERIC_COMPOSITE : ArticleType.NUMERIC;
        String mask = ArticleTextUtil.buildMask(trimmed);

        return Optional.of(new ArticleMatch(trimmed, normalized, mask, type));
    }
}
