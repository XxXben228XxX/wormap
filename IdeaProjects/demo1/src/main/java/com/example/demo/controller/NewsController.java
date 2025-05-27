package com.example.demo.controller;

import com.example.demo.entity.News;
import com.example.demo.mod.GeminiGeoLocator;
import com.example.demo.mod.NewsItem;
import com.example.demo.service.NewsService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api") // Змінено на /api, щоб ендпоінти були /api/news-data, /api/news-without-coordinates
@CrossOrigin(origins = "*")
public class NewsController {

    private final NewsService newsService;
    private static final Logger logger = LoggerFactory.getLogger(NewsController.class);
    // Використовуємо NEWS_FILE_PATH для новин БЕЗ координат
    private static final String NEWS_WITHOUT_COORDS_FILE_PATH = "news_without_coordinates.txt";
    // Використовуємо DATA_NEWS_JSON_PATH для GeoJSON новин З координатами
    private static final String DATA_NEWS_JSON_PATH = "data/news.json";

    private final GeminiGeoLocator geoLocator;

    @Autowired
    public NewsController(NewsService newsService, GeminiGeoLocator geoLocator) {
        this.newsService = newsService;
        this.geoLocator = geoLocator;
    }

    @PostMapping("/news") // Шлях /api/news
    public ResponseEntity<News> createNews(@RequestBody News news) {
        news.setTitle("Some Title");
        news.setContent("Some Content");
        News savedNews = newsService.saveNews(news);
        return new ResponseEntity<>(savedNews, HttpStatus.CREATED);
    }

    @GetMapping("/news/{id}") // Шлях /api/news/{id}
    public ResponseEntity<News> getNewsById(@PathVariable Long id) {
        Optional<News> news = newsService.getNewsById(id);
        return news.map(value -> new ResponseEntity<>(value, HttpStatus.OK))
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/news/latest") // Шлях /api/news/latest
    public List<NewsItem> getLatestNews() throws IOException {
        List<NewsItem> newsList = fetchNewsFromRSS();
        for (NewsItem news : newsList) {
            String location = geoLocator.getLocationFromText(news.getDescription());
            String coords = getCoordinates(location);
            news.setLocation(location);
            news.setCoordinates(coords);
        }
        return newsList;
    }

    public List<NewsItem> fetchNewsFromRSS() throws IOException {
        String url = "https://www.ukr.net/news/russianaggression.html";
        try {
            Document doc = Jsoup.connect(url).get();
            Elements newsItems = doc.select(".item--news");

            List<NewsItem> newsList = new ArrayList<>();
            if (!newsItems.isEmpty()) {
                // Беремо перший елемент, як у вашому оригінальному коді
                org.jsoup.nodes.Element firstItem = newsItems.first();
                NewsItem news = new NewsItem();
                news.setTitle(firstItem.select(".item--news__title a").text());
                news.setLink(firstItem.select(".item--news__title a").attr("href"));
                news.setDescription(firstItem.select(".item--news__text").text());
                newsList.add(news);
            }
            return newsList;
        } catch (IOException e) {
            logger.error("Помилка під час отримання новин з RSS: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public String getCoordinates(String location) {
        try {
            String url = "https://nominatim.openstreetmap.org/search?q=" + location + "&format=json";
            Document doc = Jsoup.connect(url).ignoreContentType(true).get();
            String jsonResponse = doc.text();
            JSONArray jsonArray = new JSONArray(jsonResponse);
            if (jsonArray.length() > 0) {
                String lat = jsonArray.getJSONObject(0).getString("lat");
                String lon = jsonArray.getJSONObject(0).getString("lon");
                return lat + ", " + lon;
            }
        } catch (IOException e) {
            logger.error("Помилка отримання координат для {}: {}", location, e.getMessage());
        }
        return "Координати не визначено.";
    }

    /**
     * Ендпоінт для отримання новин без координат у текстовому форматі.
     * Відповідає шляху `/api/news-without-coordinates` у вашому фронтенді.
     */
    @GetMapping("/news-without-coordinates")
    public ResponseEntity<Resource> getNewsWithoutCoordinates() {
        try {
            // Шлях до файлу `news_without_coordinates.txt`
            // Важливо: переконайтеся, що шлях "news_without_coordinates.txt" є відносним до кореня вашого проекту Spring Boot
            // Або вкажіть абсолютний шлях, якщо він фіксований.
            // Якщо NEWS_FILE_PATH було абсолютним, то використовуйте його:
            // Path filePath = Paths.get(NEWS_FILE_PATH).toAbsolutePath().normalize();
            // Інакше, використовуйте відносний шлях, якщо файл знаходиться у корені проекту
            Path filePath = Paths.get(NEWS_WITHOUT_COORDS_FILE_PATH).toAbsolutePath().normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                logger.info("Віддається файл новин без координат: {}", filePath);
                return ResponseEntity.ok()
                        .contentType(MediaType.TEXT_PLAIN)
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"") // inline, щоб браузер відображав, а не завантажував
                        .body(resource);
            } else {
                logger.warn("Файл новин без координат не знайдено або він нечитабельний: {}", filePath);
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException ex) {
            logger.error("Неправильний URL для файлу новин без координат: {}", ex.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Ендпоінт для отримання GeoJSON новин з координатами.
     * Відповідає шляху `/api/news-data` у вашому фронтенді.
     */
    @GetMapping(value = "/news-data", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getNewsWithCoordinates() {
        logger.info("Отримано запит до /api/news-data (GeoJSON з координатами)");

        try {
            Path filePath = Paths.get(DATA_NEWS_JSON_PATH); // Шлях до файлу GeoJSON
            if (!Files.exists(filePath)) {
                logger.warn("Файл GeoJSON не знайдено: {}", filePath.toAbsolutePath());
                return ResponseEntity.ok("[]"); // Повертаємо порожній масив JSON, якщо файл не існує
            }

            String content = new String(Files.readAllBytes(filePath));
            // Перевіряємо, чи файл не порожній і чи починається з [
            if (content.trim().isEmpty() || !content.trim().startsWith("[")) {
                logger.warn("Файл GeoJSON порожній або містить некоректний JSON: {}", filePath.toAbsolutePath());
                return ResponseEntity.ok("[]"); // Повертаємо порожній масив JSON
            }

            // Перевірка на валідний JSON
            try {
                new JSONArray(content); // Спробуємо розпарсити, щоб переконатися, що це валідний JSON
            } catch (org.json.JSONException e) {
                logger.error("Файл GeoJSON містить невалідний JSON: {}. Помилка: {}", filePath.toAbsolutePath(), e.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("[]"); // Повертаємо порожній масив у випадку помилки парсингу
            }


            logger.info("Відповідь сервера для GeoJSON: успішно завантажено.");

            return ResponseEntity.ok(content);
        } catch (IOException e) {
            logger.error("❌ Помилка читання файлу GeoJSON: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("[]");
        }
    }

    // Цей метод вам більше не потрібен у контролері, оскільки GeoJSON файл вже генерується NewsParser
    // private JSONArray convertTextToJson(String text) { /* ... */ }
}