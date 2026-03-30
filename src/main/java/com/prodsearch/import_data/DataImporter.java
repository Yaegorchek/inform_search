package com.prodsearch.import_data;

import com.prodsearch.config.ElasticsearchConfig;
import com.prodsearch.import_data.model.Product;
import com.prodsearch.search.PhoneticUtil;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation;
import co.elastic.clients.elasticsearch.core.BulkResponse;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DataImporter {

    private final ElasticsearchClient client;
    private static final String INDEX = "products";
    private static final int BATCH_SIZE = 1000;

    public DataImporter() {
        this.client = ElasticsearchConfig.createClient();
    }

    public void recreateIndexAndImport(String filePath) throws Exception {
        try {
            client.indices().delete(d -> d.index(INDEX));
        } catch (Exception e) {
            System.out.println("Индекс еще не существует.");
        }

        CreateIndexResponse createResp = client.indices().create(c -> c
                .index(INDEX)
                .settings(s -> s
                        // ДОБАВЛЯЕМ ЭТУ НАСТРОЙКУ
                        .index(i -> i.maxNgramDiff(7))
                        .analysis(a -> a
                                .filter("russian_stop", f -> f.definition(d -> d.stop(st -> st.stopwords("_russian_"))))
                                .filter("russian_stemmer", f -> f.definition(d -> d.stemmer(st -> st.language("russian"))))
                                .filter("code_ngram", f -> f.definition(d -> d.ngram(n -> n.minGram(3).maxGram(8))))
                                .filter("alphanumeric", f -> f.definition(d -> d.wordDelimiter(wd -> wd
                                        .generateNumberParts(true)
                                        .catenateNumbers(true)
                                )))
                                .analyzer("title_analyzer", t -> t
                                        .custom(cust -> cust
                                                .tokenizer("standard")
                                                .filter(List.of("lowercase", "russian_stop", "russian_stemmer"))
                                        )
                                )
                                .analyzer("code_analyzer", t -> t
                                        .custom(cust -> cust
                                                .tokenizer("keyword")
                                                .filter(List.of("lowercase", "alphanumeric"))
                                        )
                                )
                                .analyzer("numeric_analyzer", t -> t
                                        .custom(cust -> cust
                                                .tokenizer("standard")
                                                .filter(List.of("lowercase", "alphanumeric", "code_ngram"))
                                        )
                                )
                        )
                )
                .mappings(m -> m
                        .properties("id", p -> p.keyword(k -> k))
                        .properties("title", p -> p.text(t -> t.analyzer("title_analyzer")))
                        .properties("manufacturer", p -> p.keyword(k -> k))
                        .properties("allCodes", p -> p
                                .text(t -> t
                                        .analyzer("code_analyzer")
                                        .fields("numeric", f -> f.text(nt -> nt.analyzer("numeric_analyzer")))
                                        .fields("keyword", f -> f.keyword(k -> k))
                                )
                        )
                        .properties("productCode", p -> p.keyword(k -> k))
                        .properties("externalId", p -> p.keyword(k -> k))
                        .properties("phonetic", p -> p.text(t -> t))
                )
        );

        if (!createResp.acknowledged()) {
            throw new RuntimeException("Index creation failed");
        }

        importProductsToElasticsearch(filePath);
    }

    public int importProductsToElasticsearch(String filePath) throws Exception {
        List<Product> products = new JsonParser().parseProductsFromFile(filePath);
        int total = 0;

        for (int i = 0; i < products.size(); i += BATCH_SIZE) {
            List<Product> batch = products.subList(i, Math.min(i + BATCH_SIZE, products.size()));
            total += bulkImport(batch);
        }

        return total;
    }

    private String cleanCode(String code) {
        return code.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
    }

    private int bulkImport(List<Product> products) throws Exception {
        List<BulkOperation> ops = new ArrayList<>();

        for (Product p : products) {
            Set<String> codesSet = new HashSet<>();

            if (p.getProductCode() != null) {
                codesSet.add(cleanCode(p.getProductCode()));
            }

            if (p.getTitle() != null) {
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("[a-zA-Z0-9/-]{4,}");
                java.util.regex.Matcher matcher = pattern.matcher(p.getTitle());
                while (matcher.find()) {
                    String found = matcher.group();
                    if (found.matches(".*\\d.*")) {
                        codesSet.add(cleanCode(found));
                    }
                }
            }

            p.setAllCodes(new ArrayList<>(codesSet));

            if (p.getProductCode() != null) {
                p.setProductCode(cleanCode(p.getProductCode()));
            }

            String rawTitle = p.getTitle() != null ? p.getTitle() : "";

            String titleOnlyText = rawTitle.replaceAll("[0-9/-]", " ").replaceAll("\\s+", " ").trim();

            if (!titleOnlyText.isEmpty()) {
                p.setPhonetic(PhoneticUtil.toPhonetic(titleOnlyText));
            } else {
                p.setPhonetic(PhoneticUtil.toPhonetic(rawTitle));
            }

            ops.add(BulkOperation.of(b -> b.index(IndexOperation.of(io -> io
                    .index(INDEX)
                    .id(p.getExternalId())
                    .document(p)
            ))));
        }

        BulkResponse resp = client.bulk(b -> b.operations(ops));
        if (resp.errors()) {
            resp.items().forEach(item -> {
                if (item.error() != null) {
                    System.err.println("Error indexing " + item.id() + ": " + item.error().reason());
                }
            });
        }

        return products.size();
    }
}