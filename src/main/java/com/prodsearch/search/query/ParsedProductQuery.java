package com.prodsearch.search.query;

import com.prodsearch.detect.ProductPartType;

public record ParsedProductQuery(
        String rawQuery,
        SearchIntent intent,
        ProductPartType partType,
        Object parsedInfo) {
}
