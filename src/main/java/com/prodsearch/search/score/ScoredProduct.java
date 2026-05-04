package com.prodsearch.search.score;

import java.util.List;

public record ScoredProduct(
        ProductCandidate candidate,
        double finalScore,
        List<String> reasons) {
}
