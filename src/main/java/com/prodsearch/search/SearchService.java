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

        if (cache.containsKey(query)) {
            return cache.get(query);
        }

        String normQuery = normalizeText(query);
        String phoneticQuery = PhoneticUtil.toPhonetic(normQuery);

        Query searchQuery = Query.of(q -> q
                .match(m -> m
                        .field("phonetic")
                        .query(phoneticQuery)
                        .fuzziness("AUTO")
                )
        );

        SearchResponse<Product> resp = client.search(sr -> sr
                        .index(INDEX)
                        .size(MAX_RESULTS)
                        .query(searchQuery),
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
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.prodsearch.import_data.model.Product;

import java.io.IOException;
import java.util.Locale;

public class SearchService {

    private final ElasticsearchClient client;
    private static final String INDEX = "products";
    private static final int MAX_RESULTS = 100;

    public SearchService(ElasticsearchClient client) {
        this.client = client;
    }

    /**
     * Основной метод поиска.
     */
    public void Search(String query) throws IOException {
        long startTime = System.currentTimeMillis();
        if (query == null || query.isBlank()) {
            System.out.println("Запрос пустой!");
            return;
        }

        System.out.println(">> Ищем: " + query);
        executeGeneralSearch(query, startTime);
    }

    /**
     * Выполняет комбинированный поиск по артикулу и названию с весами.
     */
    private void executeGeneralSearch(String originalQuery, long startTime) throws IOException {
        // 1. Подготовка
        String cleanQuery = originalQuery.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        String normQuery = normalizeText(originalQuery);
        String phoneticQuery = PhoneticUtil.toPhonetic(normQuery);

        // 2. Создание запроса через DisMax
        Query searchQuery = Query.of(q -> q
                .disMax(dm -> dm
                        .queries(
                                Query.of(q1 -> q1.term(t -> t.field("allCodes.keyword").value(cleanQuery).boost(1000f))),
                                Query.of(q2 -> q2.match(m -> m.field("allCodes.numeric").query(originalQuery).boost(100f))),
                                Query.of(q3 -> q3.match(m -> m.field("title").query(originalQuery).boost(50f))),
                                Query.of(q4 -> q4.match(m -> m
                                        .field("phonetic")
                                        .query(phoneticQuery)
                                        .fuzziness("AUTO")
                                        .boost(10f)
                                ))
                        )
                        .tieBreaker(0.7)
                )
        );

        // 3. Выполнение
        SearchResponse<Product> resp = client.search(sr -> sr
                        .index(INDEX)
                        .size(MAX_RESULTS)
                        .query(searchQuery),
                Product.class
        );

        printResults(resp, startTime);
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
                System.out.printf("%d) [Score: %.2f] Title: %s%n   Manufacturer: %s%n   ProductCode: %s%n   ID: %s%n",
                        counter,
                        hit.score() != null ? hit.score() : 0.0,
                        p.getTitle() != null ? p.getTitle() : "не указано",
                        p.getManufacturer() != null ? p.getManufacturer() : "не указано",
                        p.getProductCode() != null ? p.getProductCode() : "не указано",
                        hit.id()
                );
                System.out.println("--------------------------------------------------");
                counter++;
            }
        }
        System.out.println("Время выполнения: " + (endTime - startTime) + " мс");
        System.out.println("==================================================");
    }

    private String normalizeText(String text) {
        if (text == null) return "";
        text = text.toLowerCase(Locale.ROOT);
        text = text.replaceAll("(\\p{L})\\1+", "$1"); // Удаление удвоенных букв
        text = text.replaceAll("[^\\p{L}\\p{Nd} ]+", ""); // Удаление спецсимволов
        return text;
    }
}
