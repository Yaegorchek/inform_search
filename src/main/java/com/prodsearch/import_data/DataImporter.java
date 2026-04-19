package com.prodsearch.import_data;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodsearch.config.ElasticsearchConfig;
import com.prodsearch.import_data.model.Product;
import com.prodsearch.search.PhoneticUtil;
import com.prodsearch.search.article.ArticleExtractionService;
import com.prodsearch.search.article.ArticleMatch;
import com.prodsearch.search.article.AlphaNumericArticleAutomaton;
import com.prodsearch.search.article.NumericArticleAutomaton;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;

public class DataImporter {

    private final ElasticsearchClient client;
    private static final String INDEX = "products";
    private static final int BATCH_SIZE = 1000;
    private final ArticleExtractionService articleExtractionService;

    public DataImporter() {
        this.client = ElasticsearchConfig.createClient();
        this.articleExtractionService = new ArticleExtractionService(
                new NumericArticleAutomaton(),
                new AlphaNumericArticleAutomaton());
    }

    public void recreateIndexAndImport(String filePath) throws Exception {
        try {
            client.indices().delete(d -> d.index(INDEX));
        } catch (Exception e) {
        }

        client.indices().create(c -> c
                .index(INDEX)
                .settings(s -> s
                        .index(i -> i.maxNgramDiff(7))
                        .analysis(a -> a
                                .filter("english_stop", f -> f.definition(d -> d.stop(st -> st.stopwords("_english_"))))
                                .filter("english_stemmer",
                                        f -> f.definition(d -> d.stemmer(st -> st.language("english"))))
                                .filter("code_ngram", f -> f.definition(d -> d.ngram(n -> n.minGram(3).maxGram(8))))
                                .filter("alphanumeric", f -> f.definition(d -> d.wordDelimiter(wd -> wd
                                        .generateNumberParts(true)
                                        .catenateNumbers(true))))
                                .analyzer("title_analyzer", t -> t
                                        .custom(cust -> cust
                                                .tokenizer("standard")
                                                .filter(List.of("lowercase", "english_stop", "english_stemmer"))))
                                // Твои анализаторы для кодов без изменений
                                .analyzer("code_analyzer", t -> t
                                        .custom(cust -> cust
                                                .tokenizer("keyword")
                                                .filter(List.of("lowercase", "alphanumeric"))))
                                .analyzer("numeric_analyzer", t -> t
                                        .custom(cust -> cust
                                                .tokenizer("standard")
                                                .filter(List.of("lowercase", "alphanumeric", "code_ngram"))))))
                .mappings(m -> m
                        .properties("title", p -> p.text(t -> t.analyzer("title_analyzer")))
                        .properties("allCodes", p -> p
                                .text(t -> t
                                        .analyzer("code_analyzer")
                                        .fields("numeric", f -> f.text(nt -> nt.analyzer("numeric_analyzer")))
                                        .fields("keyword", f -> f.keyword(k -> k))))
                        .properties("articleMasks", p -> p.keyword(k -> k))
                        .properties("articleTypes", p -> p.keyword(k -> k))
                        .properties("productCode", p -> p.keyword(k -> k))
                        .properties("manufacturer", p -> p.text(t -> t.analyzer("title_analyzer")))
                        .properties("phonetic", p -> p.text(t -> t.analyzer("code_analyzer")))));

        processFileStreaming(filePath);
    }

    private void processFileStreaming(String filePath) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonFactory factory = new JsonFactory();
        List<Product> batch = new ArrayList<>();
        int totalProcessed = 0;

        try (JsonParser parser = factory.createParser(new File(filePath))) {
            while (parser.nextToken() != JsonToken.START_ARRAY) {
                if (parser.getCurrentToken() == null)
                    return;
            }

            while (parser.nextToken() == JsonToken.START_OBJECT) {
                Product p = mapper.readValue(parser, Product.class);

                prepareProductData(p);

                batch.add(p);

                if (batch.size() >= BATCH_SIZE) {
                    executeBulk(batch);
                    totalProcessed += batch.size();
                    System.out.println("Загружено: " + totalProcessed);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                executeBulk(batch);
                totalProcessed += batch.size();
            }
        }
        System.out.println("Готово! Всего объектов: " + totalProcessed);
    }

    private void prepareProductData(Product p) {
        Set<String> extractedCodes = new LinkedHashSet<>();
        Set<String> extractedMasks = new LinkedHashSet<>();
        Set<String> extractedTypes = new LinkedHashSet<>();

        if (p.getTitle() != null) {
            String cleanText = p.getTitle().replaceAll("[^а-яА-ЯёЁa-zA-Z\\s]", " ").trim();
            p.setPhonetic(PhoneticUtil.toPhonetic(cleanText));
        }

        List<ArticleMatch> matches = articleExtractionService.extractFromMany(
                Arrays.asList(p.getProductCode(), p.getTitle()));

        for (ArticleMatch match : matches) {
            if (match.getNormalizedArticle() != null && !match.getNormalizedArticle().isBlank()) {
                extractedCodes.add(match.getNormalizedArticle());
            }
            if (match.getStructuralMask() != null && !match.getStructuralMask().isBlank()) {
                extractedMasks.add(match.getStructuralMask());
            }
            if (match.getArticleType() != null) {
                extractedTypes.add(match.getArticleType().name());
            }
        }

        p.setAllCodes(new ArrayList<>(extractedCodes));
        p.setArticleMasks(new ArrayList<>(extractedMasks));
        p.setArticleTypes(new ArrayList<>(extractedTypes));
    }

    private void executeBulk(List<Product> products) throws Exception {
        List<BulkOperation> ops = new ArrayList<>();
        for (Product p : products) {
            ops.add(BulkOperation.of(b -> b.index(i -> i
                    .index(INDEX)
                    .id(p.getExternalId())
                    .document(p))));
        }
        client.bulk(b -> b.operations(ops));
    }
}
