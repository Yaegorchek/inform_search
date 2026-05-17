package com.prodsearch.import_data;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodsearch.config.ElasticsearchConfig;
import com.prodsearch.detect.ConnectingRodBearingInfo;
import com.prodsearch.detect.ConnectingRodBearingInfoDetector;
import com.prodsearch.detect.ProductPartType;
import com.prodsearch.detect.TechTextNormalizer;
import com.prodsearch.detect.WheelHubInfo;
import com.prodsearch.detect.WheelHubInfoDetector;
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
import java.util.function.Function;

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
                        .properties("phonetic", p -> p.text(t -> t.analyzer("code_analyzer")))
                        .properties("partType", p -> p.keyword(k -> k))
                        .properties("vehicleBrand", p -> p.keyword(k -> k))
                        .properties("partBrand", p -> p.keyword(k -> k))
                        .properties("oemCodes", p -> p.keyword(k -> k))
                        .properties("features", p -> p.keyword(k -> k))
                        .properties("engineModels", p -> p.keyword(k -> k))
                        .properties("repairSize", p -> p.keyword(k -> k))
                        .properties("kitType", p -> p.keyword(k -> k))
                        .properties("quantity", p -> p.integer(i -> i))
                        .properties("journalCount", p -> p.integer(i -> i))
                        .properties("cylinderCount", p -> p.integer(i -> i))
                        .properties("widthMm", p -> p.double_(d -> d))
                        .properties("hubKind", p -> p.keyword(k -> k))
                        .properties("hubThread", p -> p.keyword(k -> k))
                        .properties("hubBoltPattern", p -> p.keyword(k -> k))
                        .properties("hubGeometry", p -> p.keyword(k -> k))
                        .properties("hubTonnage", p -> p.keyword(k -> k))
                        .properties("hubBearingState", p -> p.keyword(k -> k))
                        .properties("hubPosition", p -> p.keyword(k -> k))
                        .properties("hubSide", p -> p.keyword(k -> k))
                        .properties("hubAssembly", p -> p.boolean_(b -> b))
                        .properties("hubAbs", p -> p.boolean_(b -> b))
                        .properties("hubSeries", p -> p.keyword(k -> k))));

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

        enrichPartInfo(p);
    }

    private void enrichPartInfo(Product p) {
        if (p == null || p.getTitle() == null || p.getTitle().isBlank()) {
            return;
        }

        String title = p.getTitle();

        ConnectingRodBearingInfo bearingInfo = ConnectingRodBearingInfoDetector.detect(title);
        if (bearingInfo.detected()) {
            p.setPartType(ProductPartType.CONNECTING_ROD_BEARINGS);
            p.setVehicleBrand(bearingInfo.vehicleBrand());
            p.setPartBrand(bearingInfo.bearingBrand());
            p.setEngineModels(normalizeList(bearingInfo.engineModels(), TechTextNormalizer::normalizeEngineModel));
            p.setRepairSize(bearingInfo.repairSize());
            p.setKitType(bearingInfo.kitType());
            p.setQuantity(bearingInfo.quantity());
            p.setJournalCount(bearingInfo.journalCount());
            p.setCylinderCount(bearingInfo.cylinderCount());
            p.setWidthMm(bearingInfo.widthMm());
            if (bearingInfo.features() != null) {
                p.setFeatures(new ArrayList<>(bearingInfo.features()));
            }
            p.setOemCodes(normalizeList(bearingInfo.oemCodes(), TechTextNormalizer::normalizeOemCode));
            return;
        }

        WheelHubInfo hubInfo = WheelHubInfoDetector.detect(title);
        if (hubInfo.detected()) {
            p.setPartType(ProductPartType.WHEEL_HUB);
            p.setPartBrand(hubInfo.brand());
            p.setOemCodes(normalizeList(hubInfo.oemCodes(), TechTextNormalizer::normalizeOemCode));
            p.setHubKind(hubInfo.hubKind());
            p.setHubThread(TechTextNormalizer.normalizeThread(hubInfo.threadSize()));
            p.setHubBoltPattern(TechTextNormalizer.normalizeBoltPattern(hubInfo.boltPattern()));
            p.setHubGeometry(TechTextNormalizer.normalizeGeometry(hubInfo.geometry()));
            p.setHubTonnage(TechTextNormalizer.normalizeTonnage(hubInfo.tonnage()));
            p.setHubBearingState(hubInfo.bearingState());
            p.setHubPosition(hubInfo.position());
            p.setHubSide(hubInfo.side());
            p.setHubAssembly(hubInfo.assembly());
            p.setHubAbs(hubInfo.abs());
            if (hubInfo.series() != null) {
                p.setHubSeries(List.of(hubInfo.series()));
            }
        }
    }

    private List<String> normalizeList(List<String> values, Function<String, String> normalizer) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String mapped = normalizer.apply(value);
            if (mapped != null && !mapped.isBlank()) {
                normalized.add(mapped);
            }
        }
        return normalized.isEmpty() ? null : normalized;
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
