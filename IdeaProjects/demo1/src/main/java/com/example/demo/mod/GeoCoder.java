package com.example.demo.mod;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Component
public class GeoCoder {
    private static final Logger logger = LoggerFactory.getLogger(GeoCoder.class);

    @Value("${nominatim.api.url}")
    private String apiUrl;

    public double[] getCoordinates(String location) {
        HttpClient client = HttpClient.newHttpClient();
        try {
            // Видаляємо координати, якщо вони є
            location = location.replaceAll("\\(.*?\\)", "").trim();

            String encodedLocation = URLEncoder.encode(location, StandardCharsets.UTF_8);
            String queryUrl = apiUrl + "?q=" + encodedLocation + "&format=json&limit=5"; // Додаємо "?q="

            logger.info(" Виконуємо запит: {}", queryUrl);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(queryUrl))
                    .header("User-Agent", "Mozilla/5.0")
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.error("❌ Помилка HTTP {}: {}", response.statusCode(), response.body());
                return null;
            }

            JSONArray jsonArray = new JSONArray(response.body());
            if (jsonArray.length() > 0) {
                JSONObject bestMatch = null;

                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject result = jsonArray.getJSONObject(i);
                    if (result.has("display_name") && result.getString("display_name").contains("Україна")) {
                        bestMatch = result;
                        break;
                    }
                }

                if (bestMatch == null) {
                    bestMatch = jsonArray.getJSONObject(0);
                }

                double lat = bestMatch.getDouble("lat");
                double lon = bestMatch.getDouble("lon");

                // Додаємо випадкове зміщення, якщо це місто без точної адреси
                if (!bestMatch.has("road") && !bestMatch.has("house_number")) {
                    lat += (Math.random() - 0.5) * 0.225; // Випадковий зсув до 25 км
                    lon += (Math.random() - 0.5) * 0.225;
                }

                logger.info("✅ Отримано координати для {}: {}, {}", location, lat, lon);
                return new double[]{lat, lon};
            }

        } catch (IOException | InterruptedException e) {
            logger.error("❌ Не вдалося отримати координати для {}: {}", location, e.getMessage());
        }

        logger.warn("⚠️ Не вдалося отримати координати для {}.", location);
        return null;
    }

    public String getLocation(double lat, double lon) {
        HttpClient client = HttpClient.newHttpClient();
        try {
            String queryUrl = apiUrl + lat + "," + lon + "&format=json&limit=5"; // Збільшуємо ліміт для пошуку декількох результатів

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(queryUrl))
                    .header("User-Agent", "Mozilla/5.0")
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.error("❌ Помилка HTTP {}: {}", response.statusCode(), response.body());
                return "Не вдалося отримати локацію.";
            }

            JSONArray jsonArray = new JSONArray(response.body());
            if (jsonArray.length() > 0) {
                // Шукаємо результат з Україною
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject result = jsonArray.getJSONObject(i);
                    if (result.has("display_name") && result.getString("display_name").contains("Україна")) {
                        String displayName = result.getString("display_name");
                        logger.info("Отримано локацію для координат {}: {}", lat + "," + lon, displayName);
                        return displayName;
                    }
                }

                // Якщо Україна не знайдена, повертаємо перший результат
                JSONObject firstResult = jsonArray.getJSONObject(0);
                String displayName = firstResult.getString("display_name");
                logger.info("Отримано локацію для координат {}: {}", lat + "," + lon, displayName);
                return displayName;
            }

        } catch (IOException | InterruptedException e) {
            logger.error("❌ Не вдалося отримати локацію для координат {}: {}", lat + "," + lon, e.getMessage());
        }

        logger.warn("⚠️ Не вдалося отримати локацію для координат {}.", lat + "," + lon);
        return "Не вдалося отримати локацію.";
    }
}