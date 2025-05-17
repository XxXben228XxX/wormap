package com.example.demo.mod;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import opennlp.tools.tokenize.SimpleTokenizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class NewsParser {
    private static final Logger logger = LoggerFactory.getLogger(NewsParser.class);
    private static final String NEWS_FILE_PATH = "news_without_coordinates.txt";
    private static final String PROCESSED_LINKS_FILE = "processed_links.txt";

    @Autowired
    private GeoCoder geoCoder;

    @Autowired
    private GeminiGeoLocator geoLocator;

    private Set<String> processedLinks = new HashSet<>();
    private Map<String, List<String>> coordinatesToLinks = new HashMap<>();
    private Map<String, String> coordinatesToTitle = new HashMap<>();
    private Set<String> addedNews = new HashSet<>(); // Додаємо HashSet для унікальних новин

    public synchronized String parseNews(String url) {
        loadProcessedLinks();
        try {
            logger.info("З'єднання з: {}", url);
            Document doc = Jsoup.connect(url).get();
            logger.info("З'єднання успішне");
            Element firstItem = doc.selectFirst("section.im");

            if (firstItem != null) {
                logger.info("Знайдено елемент section.im");
                List<Element> titleElements = firstItem.select("a.im-tl_a");

                for (Element titleElement : titleElements) {
                    String title = titleElement.text();
                    String link = titleElement.attr("href");

                    if (processedLinks.contains(link)) {
                        logger.info("Новина вже оброблена: {}", link);
                        continue; // Переходимо до наступного посилання
                    }

                    logger.info("Заголовок: {}", title);
                    logger.info("Посилання: {}", link);

                    processedLinks.add(link);
                    saveProcessedLink(link);

                    String location = geoLocator.getLocationFromText(title);
                    if (location.equals("Місце не знайдено.")) {
                        logger.warn("Локація не знайдена в заголовку: {}", title);

                        try {
                            Document articleDoc = Jsoup.connect(link).get();
                            String articleText = articleDoc.body().text();
                            location = geoLocator.getLocationFromText(articleText);

                            if (location.equals("Місце не знайдено.")) {
                                // Викликаємо findLocationManually лише якщо geoLocator не знайшов локацію
                                String manualLocation = findLocationManually(title);
                                if (manualLocation != null) {
                                    location = manualLocation;
                                    logger.info("Локація знайдена вручну: {}", location);
                                } else {
                                    logger.warn("Локація не знайдена в тексті статті: {}", link);
                                    createNewsFile(title, link);
                                    continue; // Переходимо до наступного посилання
                                }
                            }
                        } catch (IOException e) {
                            logger.error("Помилка отримання тексту статті: {}", e.getMessage());
                            continue; // Переходимо до наступного посилання
                        }
                    }

                    double[] coordinates = geoCoder.getCoordinates(location);
                    if (coordinates == null) {
                        logger.warn("Координати не знайдені для локації: {}", location);
                        createNewsFile(title, link);
                        continue; // Переходимо до наступного посилання
                    }

                    String coordinatesKey = coordinates[1] + "," + coordinates[0];

                    if (coordinatesToLinks.containsKey(coordinatesKey)) {
                        List<String> links = coordinatesToLinks.get(coordinatesKey);
                        if (!links.contains(link)) {
                            links.add(link);
                            coordinatesToTitle.put(coordinatesKey, title);
                        } else {
                            logger.info("Новина з таким посиланням вже додана до цієї локації: {}", link);
                        }
                    } else {
                        List<String> links = new ArrayList<>();
                        links.add(link);
                        coordinatesToLinks.put(coordinatesKey, links);
                        coordinatesToTitle.put(coordinatesKey, title);
                    }
                }
                updateGeoJsonFile(); // Оновлюємо GeoJSON після обробки всіх новин
            } else {
                logger.warn("❌ Новини не знайдено.");
                return "❌ Новини не знайдено.";
            }
        } catch (IOException e) {
            logger.error("❌ Помилка під час з'єднання: {}", e.getMessage());
            return "❌ Помилка під час з'єднання: " + e.getMessage();
        }
        return null; // Видалено return null;
    }
    private String findLocationManually(String text) {
        SimpleTokenizer tokenizer = SimpleTokenizer.INSTANCE;
        String[] tokens = tokenizer.tokenize(text);
        Set<String> cities = loadCitiesFromDatabase(); // Завантажити базу даних міст

        for (String token : tokens) {
            if (Character.isUpperCase(token.charAt(0)) && token.length() > 2) {
                for (String city : cities) {
                    if (city.length() > 2 &&
                            token.substring(0, Math.min(3, token.length())).equalsIgnoreCase(city.substring(0, Math.min(3, city.length())))) {
                        // Знайдено співпадіння
                        return city;
                    }
                }
            }
        }
        return null;
    }
    private Set<String> loadCitiesFromDatabase() {
        Set<String> cities = new HashSet<>();
        ClassPathResource resource = new ClassPathResource("training_data.txt");
        try (InputStream inputStream = resource.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"))) {
            String line;
            Pattern pattern = Pattern.compile("<START:location>\\s*(.*?)\\s*<END>");
            while ((line = reader.readLine()) != null) {
                Matcher matcher = pattern.matcher(line);
                while (matcher.find()) {
                    cities.add(matcher.group(1).trim());
                }
            }
            logger.info("Завантажено {} міст з бази даних.", cities.size());
        } catch (IOException e) {
            logger.error("Помилка завантаження бази даних міст: {}", e.getMessage());
        } catch (NullPointerException e) {
            logger.error("Файл training_data.txt не знайдено в classpath.", e.getMessage());
        }
        return cities;
    }

    public static String createGeoJson(String location, String link, double[] coordinates) {
        return "{ \"type\": \"Feature\", " +
                "\"properties\": { \"name\": \"" + location + "\", " +
                "\"url\": \"" + link + "\" }, " +
                "\"geometry\": { \"coordinates\": [" + coordinates[1] + ", " + coordinates[0] + "], \"type\": \"Point\" } }";
    }

    private void updateGeoJsonFile() {
        String filePath = "data/news.json"; // Відносно кореня проєкту
        File file = new File(filePath);

        // Створюємо файл, якщо він не існує
        if (!file.exists()) {
            try {
                if (file.createNewFile()) {
                    logger.info("Файл GeoJSON створено: {}", file.getAbsolutePath());
                } else {
                    logger.error("❌ Не вдалося створити файл GeoJSON: {}", file.getAbsolutePath());
                    return;
                }
            } catch (IOException e) {
                logger.error("❌ Помилка створення файлу GeoJSON: {}", e.getMessage());
                return;
            }
        }

        logger.info("Оновлення файлу GeoJSON: {}", file.getAbsolutePath());

        JSONArray geoJsonArray = new JSONArray();

        // Зчитуємо існуючий JSON, якщо він є
        if (file.length() > 0) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line);
                }
                if (content.length() > 0) {
                    geoJsonArray = new JSONArray(content.toString());
                }
            } catch (IOException e) {
                logger.warn("Файл GeoJSON не знайдено або порожній, створюємо новий.");
            }
        } else {
            logger.warn("Файл GeoJSON порожній, створюємо новий.");
        }

        for (Map.Entry<String, List<String>> entry : coordinatesToLinks.entrySet()) {
            String[] coords = entry.getKey().split(",");
            double lon = Double.parseDouble(coords[0]);
            double lat = Double.parseDouble(coords[1]);
            List<String> links = entry.getValue();

            String articleTitle = coordinatesToTitle.get(entry.getKey());

            boolean found = false;

            // Перевіряємо, чи існують вже ці координати
            for (int i = 0; i < geoJsonArray.length(); i++) {
                JSONObject feature = geoJsonArray.getJSONObject(i);
                JSONObject geometry = feature.getJSONObject("geometry");
                JSONArray existingCoords = geometry.getJSONArray("coordinates");

                if (existingCoords.getDouble(0) == lon && existingCoords.getDouble(1) == lat) {
                    // Додаємо нові посилання, якщо вони ще не в списку
                    JSONObject properties = feature.getJSONObject("properties");
                    JSONArray urlArray = properties.getJSONArray("url");

                    for (String link : links) {
                        if (!urlArray.toList().contains(link)) {
                            urlArray.put(link);
                        }
                    }
                    if (articleTitle != null) {
                        properties.put("title", articleTitle);
                    }
                    found = true;
                    break;
                }
            }

            // Якщо координати відсутні, додаємо новий об'єкт
            if (!found) {
                JSONObject newFeature = new JSONObject();
                newFeature.put("type", "Feature");

                JSONObject properties = new JSONObject();
                properties.put("name", geoCoder.getLocation(lat, lon));
                properties.put("url", new JSONArray(links));

                if (articleTitle != null) {
                    properties.put("title", articleTitle);
                }

                JSONObject geometry = new JSONObject();
                geometry.put("type", "Point");
                geometry.put("coordinates", new JSONArray(new double[]{lon, lat}));

                newFeature.put("properties", properties);
                newFeature.put("geometry", geometry);

                geoJsonArray.put(newFeature);
            }
        }

        // Записуємо оновлений JSON у файл
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(geoJsonArray.toString(4)); // Pretty print JSON
            logger.info("GeoJSON оновлено.");
        } catch (IOException e) {
            logger.error("❌ Помилка запису у файл: {}", e.getMessage());
        }
    }

    private void createNewsFile(String title, String link) {
        String newsKey = title + link;
        if (!addedNews.contains(newsKey)) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(NEWS_FILE_PATH, true))) {
                LocalDateTime now = LocalDateTime.now();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                String formattedDateTime = now.format(formatter);

                writer.write("Дата та час: " + formattedDateTime + "\n");
                writer.write("Назва: " + title + "\n");
                writer.write("Посилання: " + link + "\n");
                writer.newLine();

                addedNews.add(newsKey);
                logger.info("Додано унікальну новину до файлу: {}", NEWS_FILE_PATH);
            } catch (IOException e) {
                logger.error("❌ Помилка додавання до файлу: {}", e.getMessage());
            }
        } else {
            logger.info("Новина вже існує у файлі: {}", NEWS_FILE_PATH);
        }
    }

    private void loadProcessedLinks() {
        try {
            logger.info("Завантаження оброблених посилань з: {}", PROCESSED_LINKS_FILE);
            List<String> lines = Files.readAllLines(Paths.get(PROCESSED_LINKS_FILE));
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    processedLinks.add(line.trim());
                }
            }
            logger.info("Завантажено {} оброблених посилань.", processedLinks.size());
        } catch (IOException e) {
            logger.warn("Файл оброблених посилань не знайдено. Створюємо новий.");
        }
    }

    private void saveProcessedLink(String link) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(PROCESSED_LINKS_FILE, true))) {
            writer.write(link);
            writer.newLine();
            logger.info("Посилання збережено у файл: {}", link);
        } catch (IOException e) {
            logger.error("Помилка запису оброблених посилань: {}", e.getMessage());
        }
    }
}
