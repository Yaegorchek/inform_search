package com.prodsearch.search.article;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts article-like identifiers from arbitrary text using
 * deterministic finite automata over sliding token windows.
 *
 * <p>
 * Strategy:
 * <ol>
 * <li>Normalize input for scanning (keep letters/digits/separators, collapse
 * spaces)</li>
 * <li>Split into tokens</li>
 * <li>Build candidate windows with size 1..maxWindowTokens</li>
 * <li>Run both automata on each candidate</li>
 * <li>Deduplicate by normalized form, preferring longer raw form</li>
 * </ol>
 */
public class ArticleExtractionService {

    private static final int DEFAULT_MAX_WINDOW_TOKENS = 5;

    private final ArticleAutomaton numericAutomaton;
    private final ArticleAutomaton alphaNumericAutomaton;
    private final int maxWindowTokens;

    public ArticleExtractionService(ArticleAutomaton numericAutomaton, ArticleAutomaton alphaNumericAutomaton) {
        this(numericAutomaton, alphaNumericAutomaton, DEFAULT_MAX_WINDOW_TOKENS);
    }

    public ArticleExtractionService(ArticleAutomaton numericAutomaton,
            ArticleAutomaton alphaNumericAutomaton,
            int maxWindowTokens) {
        this.numericAutomaton = numericAutomaton;
        this.alphaNumericAutomaton = alphaNumericAutomaton;
        this.maxWindowTokens = Math.max(1, maxWindowTokens);
    }

    /**
     * Extracts article matches from text.
     *
     * @param text arbitrary user/product text
     * @return deduplicated matches in stable insertion order
     */
    public List<ArticleMatch> extract(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String scanText = cleanForScanning(text);
        if (scanText.isBlank()) {
            return List.of();
        }

        List<String> tokens = splitTokens(scanText);
        if (tokens.isEmpty()) {
            return List.of();
        }

        Map<String, ArticleMatch> dedup = new LinkedHashMap<>();

        for (String token : tokens) {
            tryAddMatch(token, dedup);
        }

        for (int start = 0; start < tokens.size(); start++) {
            int maxEndExclusive = Math.min(tokens.size(), start + maxWindowTokens);
            for (int endExclusive = start + 2; endExclusive <= maxEndExclusive; endExclusive++) {
                String candidate = joinTokens(tokens, start, endExclusive);
                tryAddMatch(candidate, dedup);
            }
        }

        return new ArrayList<>(dedup.values());
    }

    /**
     * Extracts from multiple text fields and returns one deduplicated list.
     */
    public List<ArticleMatch> extractFromMany(Collection<String> textParts) {
        if (textParts == null || textParts.isEmpty()) {
            return List.of();
        }

        Map<String, ArticleMatch> dedup = new LinkedHashMap<>();
        for (String part : textParts) {
            List<ArticleMatch> partMatches = extract(part);
            for (ArticleMatch m : partMatches) {
                mergeByNormalized(m, dedup);
            }
        }
        return new ArrayList<>(dedup.values());
    }

    /**
     * Lightweight query-level helper.
     */
    public boolean containsArticle(String text) {
        return !extract(text).isEmpty();
    }

    private void tryAddMatch(String candidate, Map<String, ArticleMatch> dedup) {
        if (candidate == null || candidate.isBlank()) {
            return;
        }

        numericAutomaton.tryMatch(candidate).ifPresent(match -> mergeByNormalized(match, dedup));
        alphaNumericAutomaton.tryMatch(candidate).ifPresent(match -> mergeByNormalized(match, dedup));
    }

    private void mergeByNormalized(ArticleMatch match, Map<String, ArticleMatch> dedup) {
        if (match == null || match.isEmpty()) {
            return;
        }

        String key = match.getNormalizedArticle();
        ArticleMatch existing = dedup.get(key);

        if (existing == null) {
            dedup.put(key, match);
            return;
        }

        if (match.rawLengthScore() > existing.rawLengthScore()) {
            dedup.put(key, match);
        }
    }

    private String cleanForScanning(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        boolean previousWasSpace = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            CharKind kind = ArticleTextUtil.classify(c);

            if (kind == CharKind.LETTER || kind == CharKind.DIGIT || kind == CharKind.SEPARATOR) {
                sb.append(c);
                previousWasSpace = false;
            } else {
                if (!previousWasSpace) {
                    sb.append(' ');
                    previousWasSpace = true;
                }
            }
        }

        return sb.toString().trim();
    }

    private List<String> splitTokens(String text) {
        String[] raw = text.trim().split("\\s+");
        List<String> tokens = new ArrayList<>(raw.length);
        for (String token : raw) {
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private String joinTokens(List<String> tokens, int start, int endExclusive) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < endExclusive; i++) {
            if (i > start) {
                sb.append(' ');
            }
            sb.append(tokens.get(i));
        }
        return sb.toString();
    }
}
