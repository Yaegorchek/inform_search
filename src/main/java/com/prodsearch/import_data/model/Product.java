package com.prodsearch.import_data.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.prodsearch.detect.ProductPartType;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class Product {
    private String externalId;
    private String title;
    @JsonProperty("product_code")
    private String productCode;
    private String manufacturer;
    private List<String> allCodes;
    private List<String> articleMasks;
    private List<String> articleTypes;
    private String phonetic;

    // New fields
    private ProductPartType partType;
    private String vehicleBrand;
    private String partBrand;
    private List<String> oemCodes;
    private List<String> features;

    // For CONNECTING_ROD_BEARINGS
    private List<String> engineModels;
    private String repairSize;
    private String kitType;
    private Integer quantity;
    private Integer journalCount;
    private Integer cylinderCount;
    private Double widthMm;

    // For WHEEL_HUB
    private String hubKind;
    private String hubThread;
    private String hubBoltPattern;
    private String hubGeometry;
    private String hubTonnage;
    private String hubBearingState;
    private String hubPosition;
    private String hubSide;
    private Boolean hubAssembly;
    private Boolean hubAbs;
    private List<String> hubSeries;
}
