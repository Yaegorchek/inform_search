package com.prodsearch.search.score;

import com.prodsearch.search.query.ParsedProductQuery;
import com.prodsearch.search.query.SearchIntent;
import com.prodsearch.detect.ProductPartType;
import com.prodsearch.detect.ConnectingRodBearingInfo;
import com.prodsearch.detect.WheelHubInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ProductScoreCalculator {
    public static ScoredProduct score(ParsedProductQuery query, ProductCandidate candidate) {
        double score = candidate.elasticScore() * 1.0;
        List<String> reasons = new ArrayList<>();
        reasons.add("base elasticScore: " + candidate.elasticScore());

        if (query.partType() != ProductPartType.UNKNOWN) {
            if (candidate.partType() == query.partType()) {
                score += 100;
                reasons.add("part type matched exactly: +100");
            } else {
                score -= 120;
                reasons.add("part type mismatch: -120");
            }
        }

        String cTitle = candidate.title() != null ? candidate.title().toLowerCase() : "";
        if (!cTitle.isEmpty()) {
            boolean allMainTokens = false;
            String rawQuery = query.rawQuery() != null ? query.rawQuery() : "";
            List<String> tokens = Arrays.asList(rawQuery.toLowerCase().split("\\s+"));
            if (!tokens.isEmpty()) {
                allMainTokens = true;
                for (String token : tokens) {
                    if (!cTitle.contains(token)) {
                        allMainTokens = false;
                        break;
                    }
                }
                if (allMainTokens) {
                    score += 10;
                    reasons.add("title has all main query tokens: +10");
                }
            }

            String trimmedTitle = cTitle.trim();
            if (trimmedTitle.equals("вкладыши шатунные") || trimmedTitle.equals("ступица")) {
                score -= 30;
                reasons.add("title is too generic: -30");
            }
        }

        if (query.partType() == ProductPartType.CONNECTING_ROD_BEARINGS ||
                (query.partType() == ProductPartType.UNKNOWN
                        && candidate.partType() == ProductPartType.CONNECTING_ROD_BEARINGS)) {

            ConnectingRodBearingInfo qb = query.parsedInfo() instanceof ConnectingRodBearingInfo
                    ? (ConnectingRodBearingInfo) query.parsedInfo()
                    : null;

            if (qb != null) {
                if (qb.engineModels() != null && candidate.engineModels() != null) {
                    int engineMatches = 0;
                    for (String qem : qb.engineModels()) {
                        if (candidate.engineModels().contains(qem)) {
                            engineMatches++;
                        }
                    }
                    if (engineMatches > 0) {
                        score += 60;
                        reasons.add("engine model matched: +60");
                        if (engineMatches > 1) {
                            score += 20 * (engineMatches - 1);
                            reasons.add("extra engine models matched (" + (engineMatches - 1) + "): +"
                                    + (20 * (engineMatches - 1)));
                        }
                    } else if (!qb.engineModels().isEmpty()) {
                        if (candidate.engineModels().isEmpty()) {
                            score -= 40;
                            reasons.add("query has engine models but candidate has none: -40");
                        }
                    }
                }

                if (qb.vehicleBrand() != null && qb.vehicleBrand().equalsIgnoreCase(candidate.vehicleBrand())) {
                    score += 40;
                    reasons.add("vehicle brand matched: +40");
                }
                if (qb.bearingBrand() != null && qb.bearingBrand().equalsIgnoreCase(candidate.partBrand())) {
                    score += 30;
                    reasons.add("part brand matched: +30");
                }

                if ((candidate.vehicleBrand() == null || candidate.vehicleBrand().isEmpty()) &&
                        (candidate.engineModels() == null || candidate.engineModels().isEmpty())) {
                    score -= 25;
                    reasons.add("candidate without vehicle brand and engine models: -25");
                }

                if (qb.repairSize() != null && candidate.repairSize() != null) {
                    if (qb.repairSize().equalsIgnoreCase(candidate.repairSize())) {
                        score += 45;
                        reasons.add("repair size matched: +45");
                    } else {
                        score -= 90;
                        reasons.add("repair size mismatch: -90");
                    }
                }

                if (qb.features() != null && candidate.features() != null) {
                    int featureMatches = 0;
                    for (String qf : qb.features()) {
                        if (candidate.features().contains(qf)) {
                            featureMatches++;
                        }
                    }
                    if (featureMatches > 0) {
                        score += 15 * featureMatches;
                        reasons.add("features matched (" + featureMatches + "): +" + (15 * featureMatches));
                    }
                }

                if (qb.kitType() != null && qb.kitType().equalsIgnoreCase(candidate.kitType())) {
                    score += 25;
                    reasons.add("kit type matched: +25");
                }

                if (qb.oemCodes() != null && candidate.oemCodes() != null) {
                    boolean oemMatch = false;
                    for (String qoem : qb.oemCodes()) {
                        if (candidate.oemCodes().contains(qoem)) {
                            oemMatch = true;
                            break;
                        }
                    }
                    if (oemMatch) {
                        score += 40;
                        reasons.add("OEM/product codes matched: +40");
                    }
                }
            }

            if (query.intent() == SearchIntent.TECH_INFO) {
                boolean hasEngine = candidate.engineModels() != null && !candidate.engineModels().isEmpty();
                boolean hasSize = candidate.repairSize() != null && !candidate.repairSize().isEmpty();
                boolean hasPartBrand = candidate.partBrand() != null && !candidate.partBrand().isEmpty();
                if (hasEngine && (hasSize || hasPartBrand)) {
                    score += 30;
                    reasons.add("TECH_INFO intent and candidate has necessary fields: +30");
                }
            }

            if (cTitle.equals("вкладыши шатунные") || cTitle.startsWith("вкладыши шатунные") && cTitle.length() < 25) {
                score -= 20;
                reasons.add("title is almost only 'вкладыши шатунные': -20");
            }
        }

        if (query.partType() == ProductPartType.WHEEL_HUB ||
                (query.partType() == ProductPartType.UNKNOWN && candidate.partType() == ProductPartType.WHEEL_HUB)) {

            WheelHubInfo qh = query.parsedInfo() instanceof WheelHubInfo
                    ? (WheelHubInfo) query.parsedInfo()
                    : null;

            if (qh != null) {
                if (qh.threadSize() != null && qh.threadSize().equalsIgnoreCase(candidate.hubThread())) {
                    score += 70;
                    reasons.add("hub thread matched: +70");
                }
                if (qh.boltPattern() != null && qh.boltPattern().equalsIgnoreCase(candidate.hubBoltPattern())) {
                    score += 60;
                    reasons.add("hub bolt pattern matched: +60");
                }
                if (qh.geometry() != null && qh.geometry().equalsIgnoreCase(candidate.hubGeometry())) {
                    score += 80;
                    reasons.add("hub geometry matched completely: +80");
                }

                if (qh.bearingState() != null && candidate.hubBearingState() != null) {
                    if (qh.bearingState().equalsIgnoreCase(candidate.hubBearingState())) {
                        score += 35;
                        reasons.add("hub bearing state matched: +35");
                    } else if ((qh.bearingState().equals("WITH_BEARINGS")
                            && candidate.hubBearingState().equals("WITHOUT_BEARINGS")) ||
                            (qh.bearingState().equals("WITHOUT_BEARINGS")
                                    && candidate.hubBearingState().equals("WITH_BEARINGS"))) {
                        score -= 70;
                        reasons.add("hub bearing state mismatch (WITH vs WITHOUT): -70");
                    }
                }

                if (qh.brand() != null && (qh.brand().equalsIgnoreCase(candidate.vehicleBrand()) ||
                        qh.brand().equalsIgnoreCase(candidate.partBrand()))) {
                    score += 35;
                    reasons.add("vehicle/part brand matched for hub: +35");
                }

                if (qh.tonnage() != null && qh.tonnage().equalsIgnoreCase(candidate.hubTonnage())) {
                    score += 25;
                    reasons.add("hub tonnage matched: +25");
                }

                if (qh.position() != null && qh.position().equalsIgnoreCase(candidate.hubPosition())) {
                    score += 20;
                    reasons.add("hub position matched: +20");
                }

                if (qh.side() != null && qh.side().equalsIgnoreCase(candidate.hubSide())) {
                    score += 20;
                    reasons.add("hub side matched: +20");
                }

                if (Boolean.TRUE.equals(qh.abs())) {
                    if ((candidate.features() != null && candidate.features().contains("ABS"))
                            || cTitle.contains("abs")) {
                        score += 15;
                        reasons.add("ABS matched: +15");
                    }
                }

                if (Boolean.TRUE.equals(qh.assembly())) {
                    if ((candidate.features() != null && candidate.features().contains("ASSEMBLY"))
                            || cTitle.contains("в сборе")) {
                        score += 15;
                        reasons.add("ASSEMBLY matched: +15");
                    }
                }

                if (qh.series() != null && candidate.hubSeries() != null) {
                    if (candidate.hubSeries().contains(qh.series())) {
                        score += 15;
                        reasons.add("hub series matched (1): +15");
                    }
                }

                if (qh.oemCodes() != null && candidate.oemCodes() != null) {
                    boolean oemMatch = false;
                    for (String qoem : qh.oemCodes()) {
                        if (candidate.oemCodes().contains(qoem)) {
                            oemMatch = true;
                            break;
                        }
                    }
                    if (oemMatch) {
                        score += 40;
                        reasons.add("OEM/product codes matched: +40");
                    }
                }
            }

            if (cTitle.equals("ступица") &&
                    (candidate.hubGeometry() == null || candidate.hubGeometry().isEmpty()) &&
                    (candidate.partBrand() == null || candidate.partBrand().isEmpty()) &&
                    (candidate.hubPosition() == null || candidate.hubPosition().isEmpty())) {
                score -= 25;
                reasons.add("title is just 'ступица' without geo/brand/pos: -25");
            }
        }

        return new ScoredProduct(candidate, score, reasons);
    }
}
