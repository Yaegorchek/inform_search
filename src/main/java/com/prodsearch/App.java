package com.prodsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.prodsearch.config.ElasticsearchConfig;
import com.prodsearch.import_data.DataImporter;
import com.prodsearch.search.SearchService;

import java.util.Scanner;

public class App {
    public static void main(String[] args) {

        try {
            ElasticsearchClient client = ElasticsearchConfig.createClient();
            DataImporter importer = new DataImporter();

            /*
            Для импорта данных надо раскомментировать importData и закомментировать следующие строчки:
            SearchService searchService = new SearchService(client);
            startInteractiveSearch(searchService);
            После чего запустить

            Для запуска поиска раскомментировать строчки 31-32
            И закомментировать строчку 28.
            */

            // --- Импорт данных поверх старого индекса ---
//            importData(importer);

            // --- Интерактивный поиск ---
            SearchService searchService = new SearchService(client);
            startInteractiveSearch(searchService);

        } catch (Exception e) {
            System.err.println("Ошибка подключения или обработки данных: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void importData(DataImporter importer) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("=== ИМПОРТ ДАННЫХ В ELASTICSEARCH ===");
        System.out.print("Введите путь к файлу с продуктами: ");
        String filePath = scanner.nextLine().trim();

        if (filePath.isEmpty()) {
            System.out.println("Файл не указан. Выход из программы.");
            return;
        }

        try {
            importer.recreateIndexAndImport(filePath);
            System.out.println("Импорт завершён.");
        } catch (Exception e) {
            System.err.println("Ошибка при импорте: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void startInteractiveSearch(SearchService searchService) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("=== ПОИСК ТОВАРОВ ===");
        System.out.println("Для выхода введите 'exit'");

        while (true) {
            System.out.print("Введите название или артикул товара для поиска: ");
            String query = scanner.nextLine().trim();

            if (query.equalsIgnoreCase("exit")) break;
            if (query.isEmpty()) {
                System.out.println("Запрос не может быть пустым!");
                continue;
            }

            try {
                long start = System.currentTimeMillis();
                searchService.Search(query);
                long end = System.currentTimeMillis();
                System.out.println("=".repeat(50));
            }catch (Exception e) {
            System.err.println("===== ОШИБКА ПОЛНАЯ =====");
            e.printStackTrace();

            if (e instanceof co.elastic.clients.elasticsearch._types.ElasticsearchException esEx) {
                System.err.println("===== ELASTIC ERROR BODY =====");
                System.err.println(esEx.error().toString());
            }

            Throwable cause = e.getCause();
            while (cause != null) {
                System.err.println("--- CAUSE: " + cause.getClass().getName());
                System.err.println(cause.getMessage());
                cause = cause.getCause();
            }

            System.err.println("===== КОНЕЦ ОШИБКИ =====");
        }



    }

        scanner.close();
        System.out.println("Выход из программы...");
    }
}


/*package com.prodsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.prodsearch.search.SearchService;
import com.prodsearch.import_data.model.Product;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@SpringBootApplication
public class App {
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    @Bean
    public ElasticsearchClient elasticsearchClient() {
        BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(
                AuthScope.ANY,
                new UsernamePasswordCredentials("elastic", "1234")
        );

        RestClient restClient = RestClient.builder(
                new HttpHost("localhost", 9200, "http")
        ).setHttpClientConfigCallback(httpClientBuilder ->
                httpClientBuilder.setDefaultCredentialsProvider(credsProvider)
        ).build();

        ElasticsearchClient client = new ElasticsearchClient(
                new RestClientTransport(restClient, new JacksonJsonpMapper())
        );

        // Проверка соединения при старте
        try {
            var info = client.info();
            System.out.println("Connected to Elasticsearch: " + info.version().number());
        } catch (Exception e) {
            System.err.println("Failed to connect to Elasticsearch: " + e.getMessage());
        }

        return client;
    }

    @Bean
    public SearchService searchService(ElasticsearchClient client) {
        return new SearchService(client);
    }

    // ================== Контроллер ==================
    @RestController
    @RequestMapping("/search")
    public static class SearchController {

        private final SearchService searchService;

        public SearchController(SearchService searchService) {
            this.searchService = searchService;
        }

        @PostMapping
        public Map<String, Object> search(@RequestBody Map<String, String> request) {
            String query = request.get("query");

            if (query == null || query.isBlank()) {
                return Map.of(
                        "success", false,
                        "error", "Пустой запрос"
                );
            }

            try {
                List<Map<String, Object>> results = searchService.searchByPhonetic(query);
                long elapsed = results.isEmpty() ? 0 : (long) results.get(0).getOrDefault("elapsedMs", 0.0);

                return Map.of(
                        "success", true,
                        "count", results.size(),
                        "time", elapsed,
                        "results", results
                );
            } catch (IOException e) {
                return Map.of(
                        "success", false,
                        "error", e.getMessage()
                );
            }
        }
    }
}*/


