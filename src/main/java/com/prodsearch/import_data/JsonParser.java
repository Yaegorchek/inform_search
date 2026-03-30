package com.prodsearch.import_data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodsearch.import_data.model.Product;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

public class JsonParser {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final int MAX_TITLE_LENGTH = 1000;
    private static final int MAX_PRODUCT_CODE_LENGTH = 1000;
    private static final int MAX_MANUFACTURER_LENGTH = 500;

    public List<Product> parseProductsFromFile(String filePath) throws Exception {
        List<Product> products = new ArrayList<>();

        File file = new File(filePath);
        if (!file.exists()) {
            throw new FileNotFoundException("File not found: " + filePath);
        }

        Map<String, Object> jsonMap = objectMapper.readValue(file, Map.class);

        for (Map.Entry<String, Object> entry : jsonMap.entrySet()) {
            String sqlQuery = entry.getKey();
            Object value = entry.getValue();

            System.out.println("Found SQL query: " + sqlQuery);

            if (value instanceof List) {
                List<?> items = (List<?>) value;
                System.out.println("Processing " + items.size() + " products...");

                int batchCount = 0;
                for (Object item : items) {
                    Product product = parseProductFromMap(item);
                    if (product != null) {
                        products.add(product);
                        batchCount++;

                        // Логируем прогресс каждые 10000 продуктов
                        if (batchCount % 10000 == 0) {
                            System.out.println("Processed " + batchCount + " products in current batch...");
                        }
                    }
                }
                System.out.println("Completed batch: " + batchCount + " products");
            }
        }

        return products;
    }

    private Product parseProductFromMap(Object item) {
        try {
            if (item instanceof Map) {
                Map<?, ?> productMap = (Map<?, ?>) item;

                String externalId = getStringValue(productMap, "id");
                String manufacturer = getStringValue(productMap, "manufacturer");
                String productCode = getStringValue(productMap, "product_code");
                String title = getStringValue(productMap, "title");

                if (externalId != null && externalId.length() > 500) {
                    externalId = externalId.substring(0, 500);
                    System.out.println("WARNING: external_id truncated to 500 chars");
                }

                if (manufacturer != null && manufacturer.length() > MAX_MANUFACTURER_LENGTH) {
                    manufacturer = manufacturer.substring(0, MAX_MANUFACTURER_LENGTH);
                    System.out.println("WARNING: manufacturer truncated to " + MAX_MANUFACTURER_LENGTH + " chars");
                }

                if (productCode != null && productCode.length() > MAX_PRODUCT_CODE_LENGTH) {
                    productCode = productCode.substring(0, MAX_PRODUCT_CODE_LENGTH);
                    System.out.println("WARNING: product_code truncated to " + MAX_PRODUCT_CODE_LENGTH + " chars: " +
                            productCode.substring(0, 100) + "...");
                }

                if (title != null && title.length() > MAX_TITLE_LENGTH) {
                    title = title.substring(0, MAX_TITLE_LENGTH);
                    System.out.println("WARNING: title truncated to " + MAX_TITLE_LENGTH + " chars: " +
                            title.substring(0, 100) + "...");
                }

                if (externalId == null || externalId.trim().isEmpty()) {
                    System.out.println("SKIP: Product without externalId");
                    return null;
                }

                Product product = new Product();
                product.setExternalId(externalId.trim());
                product.setManufacturer(manufacturer != null ? manufacturer.trim() : null);
                product.setProductCode(productCode != null ? productCode.trim() : null);
                product.setTitle(title != null ? title.trim() : null);

                return product;
            }
        } catch (Exception e) {
            System.err.println("Error parsing product: " + e.getMessage());
        }
        return null;
    }

    private String getStringValue(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;

        if (value instanceof String) {
            return (String) value;
        } else if (value instanceof Number) {
            return String.valueOf(value);
        } else if (value instanceof Boolean) {
            return String.valueOf(value);
        } else {
            return value.toString();
        }
    }
}