package com.prodsearch.detect;

import java.util.List;

public record WheelHubInfo(
        boolean detected,
        String hubKind,
        String brand,
        String position,
        String side,
        String bearingState,
        Boolean assembly,
        Boolean abs,
        String threadSize,
        String boltPattern,
        String geometry,
        String tonnage,
        String series,
        List<String> oemCodes) {
}
