package com.prodsearch.search.score;

import com.prodsearch.detect.ProductPartType;
import java.util.List;

public record ProductCandidate(
        String id,
        String title,
        ProductPartType partType,
        String vehicleBrand,
        String partBrand,
        List<String> engineModels,
        String repairSize,
        String kitType,
        List<String> features,
        List<String> oemCodes,

        String hubThread,
        String hubBoltPattern,
        String hubGeometry,
        String hubTonnage,
        String hubBearingState,
        String hubPosition,
        String hubSide,
        List<String> hubSeries,

        double elasticScore) {
}
