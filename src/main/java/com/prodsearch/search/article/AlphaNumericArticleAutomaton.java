package com.prodsearch.search.article;

import java.util.Optional;

/**
 * DFA for alphanumeric article recognition.
 *
 * Accepts candidates that:
 * - contain at least one LETTER and at least one DIGIT
 * - may contain separators ('-', '.', '/'), but only between alnum groups
 * - do not allow doubled separators
 * - do not end with a separator
 *
 * Examples accepted:
 * - W213
 * - E200
 * - A2710900780
 * - PC200
 * - AB12345
 * - A-1234
 * - ABC-123
 *
 * Examples rejected:
 * - ABC (no digits)
 * - 12345 (no letters; numeric automaton should handle this)
 * - A--1234 (double separator)
 * - -A123 (starts with separator)
 * - A123- (ends with separator)
 */
public class AlphaNumericArticleAutomaton implements ArticleAutomaton {

    /**
     * Explicit DFA states.
     */
    private enum State {
        START, // nothing consumed yet
        IN_ALNUM, // currently reading letters/digits
        AFTER_SEPARATOR, // separator consumed, expecting alnum
        REJECT // invalid sequence
    }

    @Override
    public Optional<ArticleMatch> tryMatch(String candidate) {
        if (candidate == null) {
            return Optional.empty();
        }

        String raw = candidate.trim();
        if (raw.isEmpty()) {
            return Optional.empty();
        }

        State state = State.START;
        boolean seenLetter = false;
        boolean seenDigit = false;
        int alnumCount = 0;

        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            CharKind kind = ArticleTextUtil.classify(ch);

            switch (state) {
                case START -> {
                    if (kind == CharKind.LETTER || kind == CharKind.DIGIT) {
                        state = State.IN_ALNUM;
                        alnumCount++;
                        if (kind == CharKind.LETTER) {
                            seenLetter = true;
                        } else {
                            seenDigit = true;
                        }
                    } else {
                        state = State.REJECT;
                    }
                }

                case IN_ALNUM -> {
                    if (kind == CharKind.LETTER || kind == CharKind.DIGIT) {
                        alnumCount++;
                        if (kind == CharKind.LETTER) {
                            seenLetter = true;
                        } else {
                            seenDigit = true;
                        }
                    } else if (kind == CharKind.SEPARATOR) {
                        state = State.AFTER_SEPARATOR;
                    } else {
                        state = State.REJECT;
                    }
                }

                case AFTER_SEPARATOR -> {
                    // Separator must be followed by alnum symbol; doubled separators are rejected.
                    if (kind == CharKind.LETTER || kind == CharKind.DIGIT) {
                        state = State.IN_ALNUM;
                        alnumCount++;
                        if (kind == CharKind.LETTER) {
                            seenLetter = true;
                        } else {
                            seenDigit = true;
                        }
                    } else {
                        state = State.REJECT;
                    }
                }

                case REJECT -> {
                    return Optional.empty();
                }
            }

            if (state == State.REJECT) {
                return Optional.empty();
            }
        }

        // Must end in alnum (not after separator), and must include both letters and
        // digits.
        if (state != State.IN_ALNUM || !seenLetter || !seenDigit || alnumCount < 2) {
            return Optional.empty();
        }

        String normalized = ArticleTextUtil.normalizeArticle(raw);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }

        String mask = ArticleTextUtil.buildMask(raw);

        return Optional.of(new ArticleMatch(
                raw,
                normalized,
                mask,
                ArticleType.ALPHANUMERIC));
    }
}
