/*package com.prodsearch.search;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.prodsearch.import_data.model.Product;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class SearchService {

    private final ElasticsearchClient client;
    private static final String INDEX = "products";
    private static final int MAX_RESULTS = 100; // ограничиваем размер выдачи для скорости

    private final Map<String, List<Map<String, Object>>> cache = new ConcurrentHashMap<>();

    public SearchService(ElasticsearchClient client) {
        this.client = client;
    }

    public List<Map<String, Object>> searchByPhonetic(String query) throws IOException {
        long startTime = System.currentTimeMillis();
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        // 1. Подготовка и распознавание артикулов
        String rawQuery = query.trim().toLowerCase();
        List<ArticleMatch> matches = articleExtractionService.extract(query);

        List<Query> shouldQueries = new ArrayList<>();
        String phoneticQuery = PhoneticUtil.toPhonetic(rawQuery);

        // 2. Логика распределения весов (Сценарии A/B/C)
        if (!matches.isEmpty()) {
            // СЦЕНАРИЙ A/B: Если найден артикул
            for (ArticleMatch match : matches) {
                String finalNormalized = match.getNormalizedArticle();

                // Точное совпадение артикула (самый высокий приоритет)
                shouldQueries.add(Query.of(q -> q
                        .term(t -> t.field("allCodes.keyword").value(finalNormalized).boost(2000f))));

                // Поиск по префиксу артикула
                shouldQueries.add(Query.of(q -> q
                        .prefix(p -> p.field("allCodes.keyword").value(finalNormalized).boost(1000f))));
            }
            // Фонетика при поиске артикула имеет минимальный вес
            shouldQueries.add(Query.of(q -> q
                    .match(m -> m.field("phonetic").query(phoneticQuery).boost(0.01f))));
        } else {
            // СЦЕНАРИЙ C: Обычный текстовый поиск
            // Поиск по названию с нечеткостью (Fuzziness)
            shouldQueries.add(Query.of(q -> q
                    .match(m -> m.field("title").query(rawQuery).fuzziness("2").boost(600f))));

            // Поиск по фонетике
            shouldQueries.add(Query.of(q -> q
                    .match(m -> m.field("phonetic").query(phoneticQuery).fuzziness("2").boost(700f))));
        }

        // 3. Сборка финального Query
        Query finalQuery = Query.of(q -> q
                .bool(b -> b.should(shouldQueries).minimumShouldMatch("1"))
        );

        SearchResponse<Product> resp = client.search(sr -> sr
                        .index(INDEX)
                        .size(MAX_RESULTS)
                        .query(finalQuery)
                        .minScore(1.0), // Отсекаем нерелевантный мусор
                Product.class
        );

        long elapsed = System.currentTimeMillis() - startTime;

        List<Map<String, Object>> results = resp.hits().hits()
                .parallelStream()
                .map(hit -> {
                    Product p = hit.source();
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", hit.id());
                    map.put("title", p.getTitle() != null ? p.getTitle() : "не указано");
                    map.put("manufacturer", p.getManufacturer() != null ? p.getManufacturer() : "не указано");
                    map.put("productCode", p.getProductCode());
                    map.put("externalId", p.getExternalId());
                    map.put("score", hit.score() != null ? hit.score() : 0.0);
                    map.put("elapsedMs", elapsed);
                    return map;
                })
                .collect(Collectors.toList());

        cache.put(query, results);

        return results;
    }

    private String normalizeText(String text) {
        text = text.toLowerCase(Locale.ROOT);
        text = text.replaceAll("(\\p{L})\\1+", "$1");
        text = text.replaceAll("[^\\p{L}\\p{Nd} ]+", "");
        return text;
    }
}*/

package com.prodsearch.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.ibm.icu.text.Transliterator;
import com.prodsearch.detect.ConnectingRodBearingInfo;
import com.prodsearch.detect.ProductPartType;
import com.prodsearch.detect.WheelHubInfo;
import com.prodsearch.import_data.model.Product;
import com.prodsearch.search.article.ArticleExtractionService;
import com.prodsearch.search.article.ArticleMatch;
import com.prodsearch.search.article.AlphaNumericArticleAutomaton;
import com.prodsearch.search.article.NumericArticleAutomaton;
import com.prodsearch.search.query.ParsedProductQuery;
import com.prodsearch.search.query.ProductQueryParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SearchService {

    private final ElasticsearchClient client;
    private static final String INDEX = "products";
    private static final Transliterator cyrToLat = Transliterator.getInstance(
            "Cyrillic-Latin; Any-Latin; NFD; [:Nonspacing Mark:] Remove; NFC");
    private static final int MAX_RESULTS = 10;
    private static final boolean DEBUG_QUERY = true;
    private final ArticleExtractionService articleExtractionService;
    private final ProductQueryParser queryParser;

    public SearchService(ElasticsearchClient client) {
        this.client = client;
        this.articleExtractionService = new ArticleExtractionService(
                new NumericArticleAutomaton(),
                new AlphaNumericArticleAutomaton());
        this.queryParser = new ProductQueryParser();
    }

    public void Search(String query) throws IOException {
        long startTime = System.currentTimeMillis();
        if (query == null || query.isBlank()) {
            System.out.println("Запрос пустой!");
            return;
        }

        System.out.println(">> Ищем: " + query);
        executeGeneralSearch(query, startTime);
    }

    private void executeGeneralSearch(String originalQuery, long startTime) throws IOException {
        // 1. Подготовка данных
        String rawQuery = originalQuery.trim().toLowerCase();

        // DFA-распознавание артикулов в запросе
        List<ArticleMatch> matches = articleExtractionService.extract(originalQuery);

        ParsedProductQuery parsedQuery = queryParser.parse(originalQuery);
        if (DEBUG_QUERY) {
            logParsedQuery(parsedQuery);
        }
        List<Query> filterQueries = buildFilterQueries(parsedQuery);

        List<Query> shouldQueries = new ArrayList<>();

        // 2. ФОНЕТИКА
        String phoneticQuery = PhoneticUtil.toPhonetic(rawQuery);

        // 3. РАСПРЕДЕЛЕНИЕ ВЕСОВ
        if (!matches.isEmpty()) {
            // СЦЕНАРИЙ A/B: АРТИКУЛ ИЛИ СМЕШАННЫЙ ЗАПРОС (текст + артикул)
            for (ArticleMatch match : matches) {
                final String finalNormalized = match.getNormalizedArticle();
                shouldQueries.add(
                        Query.of(q -> q.term(t -> t.field("allCodes.keyword").value(finalNormalized).boost(2000f))));
                shouldQueries.add(
                        Query.of(q -> q.prefix(p -> p.field("allCodes.keyword").value(finalNormalized).boost(1000f))));
            }
            // Фонетика для артикулов
            shouldQueries.add(Query.of(q -> q.match(m -> m.field("phonetic").query(phoneticQuery).boost(0.01f))));
        } else {
            // СЦЕНАРИЙ C: ОБЫЧНЫЙ ТЕКСТОВЫЙ ЗАПРОС (работает по старой логике)
            // 1. Ищем оригинал
            shouldQueries.add(Query.of(q -> q.match(m -> m.field("title").query(rawQuery).fuzziness("2").boost(600f))));

            // 2. Фонетика
            shouldQueries.add(Query.of(q -> q.match(m -> m
                    .field("phonetic")
                    .query(phoneticQuery)
                    .boost(600f))));
        }

        addPartSpecificShoulds(parsedQuery, shouldQueries);

        // 4. ВЫПОЛНЕНИЕ ЗАПРОСА
        Query finalQuery = Query.of(q -> q.bool(b -> b
                .should(shouldQueries)
                .filter(filterQueries)
                .minimumShouldMatch("1")));

        SearchResponse<Product> resp = client.search(sr -> sr
                .index(INDEX)
                .size(MAX_RESULTS)
                .query(finalQuery)
                .minScore(1.0), // Не показываем мусор
                Product.class);

        // 5. ВЫВОД
        printResults(resp, startTime);
    }

    private List<Query> buildFilterQueries(ParsedProductQuery parsedQuery) {
        List<Query> filters = new ArrayList<>();
        if (parsedQuery == null) {
            return filters;
        }

        if (parsedQuery.partType() != null && parsedQuery.partType() != ProductPartType.UNKNOWN) {
            addTermFilter(filters, "partType", parsedQuery.partType().name());
        }

        Object info = parsedQuery.parsedInfo();
        if (info instanceof ConnectingRodBearingInfo bearingInfo) {
            addTermFilter(filters, "vehicleBrand", bearingInfo.vehicleBrand());
            addTermFilter(filters, "partBrand", bearingInfo.bearingBrand());
            addTermFilter(filters, "repairSize", bearingInfo.repairSize());
            addTermFilter(filters, "kitType", bearingInfo.kitType());
            addTermFilter(filters, "quantity", bearingInfo.quantity());
            addTermFilter(filters, "journalCount", bearingInfo.journalCount());
            addTermFilter(filters, "cylinderCount", bearingInfo.cylinderCount());
            if (bearingInfo.features() != null) {
                for (String feature : bearingInfo.features()) {
                    addTermFilter(filters, "features", feature);
                }
            }
        } else if (info instanceof WheelHubInfo hubInfo) {
            addTermFilter(filters, "hubKind", hubInfo.hubKind());
            addTermFilter(filters, "hubPosition", hubInfo.position());
            addTermFilter(filters, "hubSide", hubInfo.side());
            addTermFilter(filters, "hubBearingState", hubInfo.bearingState());
            addTermFilter(filters, "hubThread", hubInfo.threadSize());
            addTermFilter(filters, "hubBoltPattern", hubInfo.boltPattern());
            addTermFilter(filters, "hubGeometry", hubInfo.geometry());
            addTermFilter(filters, "hubTonnage", hubInfo.tonnage());
            addTermFilter(filters, "hubSeries", hubInfo.series());
            addTermFilter(filters, "partBrand", hubInfo.brand());
            addTermFilter(filters, "hubAbs", hubInfo.abs());
            addTermFilter(filters, "hubAssembly", hubInfo.assembly());
        }

        return filters;
    }

    private void addPartSpecificShoulds(ParsedProductQuery parsedQuery, List<Query> shouldQueries) {
        if (parsedQuery == null) {
            return;
        }
        Object info = parsedQuery.parsedInfo();
        if (info instanceof ConnectingRodBearingInfo bearingInfo) {
            addTermsShould(shouldQueries, "engineModels", bearingInfo.engineModels(), 500f);
            addTermsShould(shouldQueries, "oemCodes", bearingInfo.oemCodes(), 800f);
        } else if (info instanceof WheelHubInfo hubInfo) {
            addTermsShould(shouldQueries, "oemCodes", hubInfo.oemCodes(), 800f);
        }
    }

    private void addTermFilter(List<Query> filters, String field, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        filters.add(Query.of(q -> q.term(t -> t.field(field).value(FieldValue.of(value)))));
    }

    private void addTermFilter(List<Query> filters, String field, Integer value) {
        if (value == null) {
            return;
        }
        filters.add(Query.of(q -> q.term(t -> t.field(field).value(FieldValue.of(value.longValue())))));
    }

    private void addTermFilter(List<Query> filters, String field, Boolean value) {
        if (value == null) {
            return;
        }
        filters.add(Query.of(q -> q.term(t -> t.field(field).value(FieldValue.of(value)))));
    }

    private void addTermsShould(List<Query> shouldQueries, String field, List<String> values, float boost) {
        List<FieldValue> fieldValues = toFieldValues(values);
        if (fieldValues.isEmpty()) {
            return;
        }
        shouldQueries.add(Query.of(q -> q
                .terms(t -> t.field(field).terms(tv -> tv.value(fieldValues)).boost(boost))));
    }

    private List<FieldValue> toFieldValues(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(FieldValue::of)
                .collect(Collectors.toList());
    }

    private void logParsedQuery(ParsedProductQuery parsedQuery) {
        System.out.println("---- PARSER DEBUG ----");
        if (parsedQuery == null) {
            System.out.println("Parsed query is null");
            System.out.println("----------------------");
            return;
        }
        System.out.println("raw: " + parsedQuery.rawQuery());
        System.out.println("intent: " + parsedQuery.intent());
        System.out.println("partType: " + parsedQuery.partType());

        List<String> filterPlan = describeFilterPlan(parsedQuery);
        if (filterPlan.isEmpty()) {
            System.out.println("filters: (none)");
        } else {
            System.out.println("filters: " + String.join(", ", filterPlan));
        }

        List<String> boostPlan = describeBoostPlan(parsedQuery);
        if (boostPlan.isEmpty()) {
            System.out.println("boosts: (none)");
        } else {
            System.out.println("boosts: " + String.join(", ", boostPlan));
        }
        System.out.println("----------------------");
    }

    private List<String> describeFilterPlan(ParsedProductQuery parsedQuery) {
        List<String> filters = new ArrayList<>();
        if (parsedQuery.partType() != null && parsedQuery.partType() != ProductPartType.UNKNOWN) {
            filters.add("partType=" + parsedQuery.partType().name());
        }

        Object info = parsedQuery.parsedInfo();
        if (info instanceof ConnectingRodBearingInfo bearingInfo) {
            addFilterInfo(filters, "vehicleBrand", bearingInfo.vehicleBrand());
            addFilterInfo(filters, "partBrand", bearingInfo.bearingBrand());
            addFilterInfo(filters, "repairSize", bearingInfo.repairSize());
            addFilterInfo(filters, "kitType", bearingInfo.kitType());
            addFilterInfo(filters, "quantity", bearingInfo.quantity());
            addFilterInfo(filters, "journalCount", bearingInfo.journalCount());
            addFilterInfo(filters, "cylinderCount", bearingInfo.cylinderCount());
            if (bearingInfo.features() != null && !bearingInfo.features().isEmpty()) {
                filters.add("features=" + String.join("/", bearingInfo.features()));
            }
        } else if (info instanceof WheelHubInfo hubInfo) {
            addFilterInfo(filters, "hubKind", hubInfo.hubKind());
            addFilterInfo(filters, "hubPosition", hubInfo.position());
            addFilterInfo(filters, "hubSide", hubInfo.side());
            addFilterInfo(filters, "hubBearingState", hubInfo.bearingState());
            addFilterInfo(filters, "hubThread", hubInfo.threadSize());
            addFilterInfo(filters, "hubBoltPattern", hubInfo.boltPattern());
            addFilterInfo(filters, "hubGeometry", hubInfo.geometry());
            addFilterInfo(filters, "hubTonnage", hubInfo.tonnage());
            addFilterInfo(filters, "hubSeries", hubInfo.series());
            addFilterInfo(filters, "partBrand", hubInfo.brand());
            addFilterInfo(filters, "hubAbs", hubInfo.abs());
            addFilterInfo(filters, "hubAssembly", hubInfo.assembly());
        }
        return filters;
    }

    private List<String> describeBoostPlan(ParsedProductQuery parsedQuery) {
        List<String> boosts = new ArrayList<>();
        Object info = parsedQuery.parsedInfo();
        if (info instanceof ConnectingRodBearingInfo bearingInfo) {
            if (bearingInfo.engineModels() != null && !bearingInfo.engineModels().isEmpty()) {
                boosts.add("engineModels=" + String.join("/", bearingInfo.engineModels()));
            }
            if (bearingInfo.oemCodes() != null && !bearingInfo.oemCodes().isEmpty()) {
                boosts.add("oemCodes=" + String.join("/", bearingInfo.oemCodes()));
            }
        } else if (info instanceof WheelHubInfo hubInfo) {
            if (hubInfo.oemCodes() != null && !hubInfo.oemCodes().isEmpty()) {
                boosts.add("oemCodes=" + String.join("/", hubInfo.oemCodes()));
            }
        }
        return boosts;
    }

    private void addFilterInfo(List<String> filters, String key, String value) {
        if (value != null && !value.isBlank()) {
            filters.add(key + "=" + value);
        }
    }

    private void addFilterInfo(List<String> filters, String key, Integer value) {
        if (value != null) {
            filters.add(key + "=" + value);
        }
    }

    private void addFilterInfo(List<String> filters, String key, Boolean value) {
        if (value != null) {
            filters.add(key + "=" + value);
        }
    }

    private void printResults(SearchResponse<Product> resp, long startTime) {
        long endTime = System.currentTimeMillis();
        if (resp.hits().hits().isEmpty()) {
            System.out.println("Ничего не найдено.");
        } else {
            System.out.println("=== РЕЗУЛЬТАТЫ ПОИСКА ===");
            int counter = 1;
            for (Hit<Product> hit : resp.hits().hits()) {
                Product p = hit.source();
                if (p == null)
                    continue;

                System.out.printf("%d) [Score: %.2f] Title: %s%n   Manufacturer: %s%n   ProductCode: %s%n   ID: %s%n",
                        counter,
                        hit.score() != null ? hit.score() : 0.0,
                        p.getTitle(),
                        p.getManufacturer(),
                        p.getProductCode() != null ? p.getProductCode() : "не указано",
                        hit.id());
                System.out.println("--------------------------------------------------");
                counter++;
            }
        }
        System.out.println("Время выполнения: " + (endTime - startTime) + " мс");
        System.out.println("==================================================");
    }
}
