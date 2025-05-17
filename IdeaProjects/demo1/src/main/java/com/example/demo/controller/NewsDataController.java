package com.example.demo.controller; // Або пакет, який ти використовуєш

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@RestController
public class NewsDataController {

    private static final Logger logger = LoggerFactory.getLogger(NewsDataController.class);
    private static final String NEWS_FILE_PATH = "/app/data/news.json"; // Шлях до файлу на Render

    @GetMapping("/api/news-data") // Цей endpoint буде доступний за URL /api/news-data
    public ResponseEntity<String> getNewsData() {
        logger.info("Отримано запит на /api/news-data");
        try {
            // Перевіряємо, чи існує файл
            if (!Files.exists(Paths.get(NEWS_FILE_PATH))) {
                logger.warn("Файл новин не знайдено за шляхом: {}", NEWS_FILE_PATH);
                // Повертаємо порожній масив JSON, якщо файл не знайдено
                return ResponseEntity.ok("[]");
            }

            // Зчитуємо весь вміст файлу
            String content = new String(Files.readAllBytes(Paths.get(NEWS_FILE_PATH)));

            logger.info("Успішно зчитано вміст файлу новин.");

            // Повертаємо вміст файлу як відповідь з типом контенту application/json
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(content);

        } catch (IOException e) {
            logger.error("❌ Помилка при читанні файлу новин з {}: {}", NEWS_FILE_PATH, e.getMessage());
            // У випадку помилки читання повертаємо статус 500 Internal Server Error
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"Помилка при читанні даних новин.\"}");
        }
    }
}