package com.prodsearch.detect;

import java.util.List;
import java.util.Set;

public record ConnectingRodBearingInfo(
        boolean detected,
        String vehicleBrand,
        String bearingBrand,
        String repairSize,
        String kitType,
        Integer quantity,
        Integer journalCount,
        Integer cylinderCount,
        Double widthMm,
        List<String> engineModels,
        Set<String> features,
        List<String> oemCodes) {
}
