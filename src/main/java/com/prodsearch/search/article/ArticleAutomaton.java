package com.prodsearch.search.article;

import java.util.Optional;

/**
 * Deterministic article recognizer contract.
 *
 * Implementations must use explicit state transitions (DFA) and return
 * a fully-populated {@link ArticleMatch} only when the input is recognized
 * as a valid article of the automaton's family.
 */
public interface ArticleAutomaton {

    /**
     * Attempts to recognize an article candidate.
     *
     * @param candidate raw text candidate (single token or multi-token window)
     * @return optional match with raw/normalized/mask/type if accepted
     */
    Optional<ArticleMatch> tryMatch(String candidate);
}
