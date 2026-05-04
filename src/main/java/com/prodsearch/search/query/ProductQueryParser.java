package com.prodsearch.search.query;

import com.prodsearch.detect.*;
import java.util.List;

public final class ProductQueryParser {

    private static final List<String> TECH_INFO_KEYWORDS = List.of(
            "характеристики", "характеристика", "тех инфа", "техническая информация",
            "параметры", "размеры", "подойдет", "подойдет ли", "что за", "инфа");

    public ParsedProductQuery parse(String rawQuery) {
        String lowerQuery = rawQuery.toLowerCase();

        SearchIntent intent = SearchIntent.FIND_PRODUCT;
        for (String kw : TECH_INFO_KEYWORDS) {
            if (lowerQuery.contains(kw)) {
                intent = SearchIntent.TECH_INFO;
                break;
            }
        }

        ConnectingRodBearingInfo bearingInfo = ConnectingRodBearingInfoDetector.detect(rawQuery);
        if (bearingInfo.detected()) {
            return new ParsedProductQuery(rawQuery, intent, ProductPartType.CONNECTING_ROD_BEARINGS, bearingInfo);
        }

        WheelHubInfo hubInfo = WheelHubInfoDetector.detect(rawQuery);
        if (hubInfo.detected()) {
            return new ParsedProductQuery(rawQuery, intent, ProductPartType.WHEEL_HUB, hubInfo);
        }

        return new ParsedProductQuery(rawQuery, intent, ProductPartType.UNKNOWN, null);
    }
}
