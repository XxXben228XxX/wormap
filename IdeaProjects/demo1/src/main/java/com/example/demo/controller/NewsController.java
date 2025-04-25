package com.example.demo.controller;

import com.example.demo.entity.News;
import com.example.demo.mod.GeminiGeoLocator;
import com.example.demo.mod.NewsItem;
import com.example.demo.service.NewsService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/news")
@CrossOrigin(origins = "*")
public class NewsController {

    private final NewsService newsService;
    private static final Logger logger = LoggerFactory.getLogger(NewsController.class);
    private static final String NEWS_FILE_PATH = "C:\\Users\\Den\\IdeaProjects\\demo1\\news_without_coordinates.txt";

    private final GeminiGeoLocator geoLocator; // Додано поле GeminiGeoLocator

    @Autowired
    public NewsController(NewsService newsService, GeminiGeoLocator geoLocator) {
        this.newsService = newsService;
        this.geoLocator = geoLocator; // Ініціалізація поля
    }

    @PostMapping
    public ResponseEntity<News> createNews(@RequestBody News news) {
        news.setTitle("Some Title");
        news.setContent("Some Content");
        News savedNews = newsService.saveNews(news);
        return new ResponseEntity<>(savedNews, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<News> getNewsById(@PathVariable Long id) {
        Optional<News> news = newsService.getNewsById(id);
        return news.map(value -> new ResponseEntity<>(value, HttpStatus.OK))
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/latest")
    public List<NewsItem> getLatestNews() throws IOException {
        List<NewsItem> newsList = fetchNewsFromRSS();
        for (NewsItem news : newsList) {
            String location = geoLocator.getLocationFromText(news.getDescription()); // Виклик через екземпляр
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
                Element firstItem = newsItems.first();
                NewsItem news = new NewsItem();
                news.setTitle(firstItem.select(".item--news__title a").text());
                news.setLink(firstItem.select(".item--news__title a").attr("href"));
                news.setDescription(firstItem.select(".item--news__text").text());
                newsList.add(news);
            }
            return newsList;
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public String getCoordinates(String location) {
        // Реалізуйте метод для отримання координат за місцем події через OpenStreetMap API (Nominatim)
        try {
            String url = "https://nominatim.openstreetmap.org/search?q=" + location + "&format=json";
            Document doc = Jsoup.connect(url).ignoreContentType(true).get();
            String jsonResponse = doc.text();
            // Розбір JSON-відповіді
            org.json.JSONArray jsonArray = new org.json.JSONArray(jsonResponse);
            if (jsonArray.length() > 0) {
                String lat = jsonArray.getJSONObject(0).getString("lat");
                String lon = jsonArray.getJSONObject(0).getString("lon");
                return lat + ", " + lon;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "Координати не визначено.";
    }
    @GetMapping("/get_news_without_coordinates")
    public ResponseEntity<String> getNewsWithoutCoordinates() {
        try {
            String content = new String(Files.readAllBytes(Paths.get(NEWS_FILE_PATH)));
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(content);
        } catch (IOException e) {
            logger.error("❌ Помилка читання файлу: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping(value = "/news.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getNewsJson() {
        logger.info("Отримано запит до /news.json"); // Додаємо логування

        try {
            String content = new String(Files.readAllBytes(Paths.get(NEWS_FILE_PATH)));
            JSONArray jsonArray = convertTextToJson(content);
            logger.info("Відповідь сервера: {}", jsonArray.toString()); // Додаємо логування

            return ResponseEntity.ok(jsonArray.toString());
        } catch (IOException e) {
            logger.error("❌ Помилка читання файлу: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    private JSONArray convertTextToJson(String text) {
        JSONArray jsonArray = new JSONArray();
        String[] newsItems = text.split("\n\n");
        for (String item : newsItems) {
            String[] lines = item.split("\n");
            if (lines.length >= 2) {
                String name = lines[0].replace("Назва: ", "").trim();
                String url = lines[1].replace("Посилання: ", "").trim();
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("name", name);
                jsonObject.put("url", url);
                jsonArray.put(jsonObject);
            }
        }
        return jsonArray;
    }
}
