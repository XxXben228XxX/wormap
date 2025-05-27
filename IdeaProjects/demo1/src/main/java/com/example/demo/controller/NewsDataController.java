package com.example.demo.controller; // Або пакет, який ти використовуєш

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets; // Додано для коректного зчитування UTF-8

@RestController
public class NewsDataController {

    private static final Logger logger = LoggerFactory.getLogger(NewsDataController.class);

    // Шлях до файлу news.json, якщо він є статичним ресурсом або змонтованим томом
    // Якщо news.json теж генерується, то його шлях також потрібно буде динамічно отримувати.
    // Наразі залишаємо так, як було, припускаючи, що він може бути змонтований або в іншому доступному місці.
    // Якщо він знаходиться в src/main/resources, то потрібно використовувати ResourceLoader, як для моделей.
    private static final String NEWS_FILE_PATH = "/app/data/news.json";

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
            String content = new String(Files.readAllBytes(Paths.get(NEWS_FILE_PATH)), StandardCharsets.UTF_8); // Вказано кодування

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

    @GetMapping("/api/news-without-coordinates")
    public ResponseEntity<String> getNewsWithoutCoordinates() {
        logger.info("Отримано запит на /api/news-without-coordinates");

        // Визначаємо шлях до файлу news_without_coordinates.txt у тимчасовій директорії
        String tempDir = System.getProperty("java.io.tmpdir");
        // Локальний шлях "demo1\\news_without_coordinates.txt" не використовується тут,
        // бо на Render.com файл буде зберігатися в тимчасовій директорії.
        String newsWithoutCoordsFilePath = tempDir + File.separator + "news_without_coordinates.txt";
        File newsWithoutCoordsFile = new File(newsWithoutCoordsFilePath);

        try {
            // Перевіряємо, чи існує файл
            if (!newsWithoutCoordsFile.exists() || newsWithoutCoordsFile.length() == 0) {
                logger.warn("Файл новин без координат не знайдено або він порожній за шляхом: {}", newsWithoutCoordsFilePath);
                // Повертаємо порожню відповідь
                return ResponseEntity.ok(""); // Повертаємо порожній рядок, якщо файл порожній або відсутній
            }

            // Зчитуємо весь вміст файлу з кодуванням UTF-8
            String content = new String(Files.readAllBytes(newsWithoutCoordsFile.toPath()), StandardCharsets.UTF_8);

            logger.info("Успішно зчитано вміст файлу новин без координат.");

            // Повертаємо вміст файлу як відповідь з типом контенту TEXT_PLAIN
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN) // Змінено на TEXT_PLAIN, оскільки формат не JSON
                    .body(content);

        } catch (IOException e) {
            logger.error("❌ Помилка при читанні файлу новин без координат з {}: {}", newsWithoutCoordsFilePath, e.getMessage());
            // У випадку помилки читання повертаємо статус 500 Internal Server Error
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Помилка при читанні даних новин без координат."); // Повертаємо простий текст помилки
        }
    }
}