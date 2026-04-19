package com.prodsearch.import_data.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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

}
