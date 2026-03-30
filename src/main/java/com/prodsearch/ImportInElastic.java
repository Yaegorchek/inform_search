package com.prodsearch;

import com.prodsearch.import_data.DataImporter;

public class ImportInElastic {
    public static void main(String[] args) {
        DataImporter importer = new DataImporter();
        String filePath = "src/data/json/offers.json";

        System.out.println("Starting full recreate + import process...");

        try {
            // Удаляем старый индекс, создаём новый с dense_vector и импортируем данные
            importer.recreateIndexAndImport(filePath);

            System.out.println("=== IMPORT COMPLETED ===");
        } catch (Exception e) {
            System.err.println("Import failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
