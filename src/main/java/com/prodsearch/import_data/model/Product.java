package com.prodsearch.import_data.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;


@Getter
@Setter
public class Product {

    private String title;
    private String manufacturer;
    private String productCode;
    private String externalId;
    private List<String> allCodes;
    private String phonetic;

}
