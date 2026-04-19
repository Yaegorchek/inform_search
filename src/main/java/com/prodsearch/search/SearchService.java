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
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.ibm.icu.text.Transliterator;
import com.prodsearch.import_data.model.Product;
import com.prodsearch.search.article.ArticleExtractionService;
import com.prodsearch.search.article.ArticleMatch;
import com.prodsearch.search.article.AlphaNumericArticleAutomaton;
import com.prodsearch.search.article.NumericArticleAutomaton;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SearchService {

    private final ElasticsearchClient client;
    private static final String INDEX = "products";
    private static final Transliterator cyrToLat = Transliterator.getInstance(
            "Cyrillic-Latin; Any-Latin; NFD; [:Nonspacing Mark:] Remove; NFC");
    private static final int MAX_RESULTS = 10;
    private final ArticleExtractionService articleExtractionService;

    public SearchService(ElasticsearchClient client) {
        this.client = client;
        this.articleExtractionService = new ArticleExtractionService(
                new NumericArticleAutomaton(),
                new AlphaNumericArticleAutomaton());
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

        // 4. ВЫПОЛНЕНИЕ ЗАПРОСА
        Query finalQuery = Query.of(q -> q.bool(b -> b.should(shouldQueries).minimumShouldMatch("1")));

        SearchResponse<Product> resp = client.search(sr -> sr
                .index(INDEX)
                .size(MAX_RESULTS)
                .query(finalQuery)
                .minScore(1.0), // Не показываем мусор
                Product.class);

        // 5. ВЫВОД
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
