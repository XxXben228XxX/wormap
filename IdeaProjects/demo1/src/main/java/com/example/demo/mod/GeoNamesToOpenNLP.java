package com.example.demo.mod;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths; // Цей імпорт більше не потрібен для читання файлів з classpath
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class GeoNamesToOpenNLP {

    @Autowired
    private GeminiGeoLocator geoLocator;

    // Додаємо ResourceLoader для доступу до ресурсів
    @Autowired
    private ResourceLoader resourceLoader;

    private Set<CityWithCoordinates> uniqueCities = new HashSet<>();
    private Set<CityWithCoordinates> writtenCities = new HashSet<>();

    public void processFiles() {
        System.out.println(">>> Запуск методу processFiles()");
        // Шляхи до вхідних файлів тепер вказують на ресурси в classpath
        String inputFile1 = "GG.txt"; // В src/main/resources
        String inputFile2 = "UA.txt"; // В src/main/resources
        String inputFileUA2 = "UA2.txt"; // В src/main/resources
        String inputFileUA3 = "UA3.txt"; // В src/main/resources
        String inputFile4 = "coordinates.json"; // В src/main/resources
        String translatedFile = "translated_all_ukr.txt"; // В src/main/resources

        // Визначаємо шлях для вихідного файлу в тимчасовій директорії
        // Це забезпечить, що файл буде доступний для запису на Render.com
        String tempDir = System.getProperty("java.io.tmpdir");
        String outputFile = tempDir + File.separator + "training_data.txt";
        System.out.println("Вихідний файл training_data.txt буде збережено за шляхом: " + outputFile);


        BufferedWriter writer = null;

        try {
            File file = new File(outputFile);
            if (!file.exists()) {
                file.createNewFile();
            }
            System.out.println("Файл training_data.txt створено або вже існує.");

            writtenCities = readCitiesFromFile(outputFile);
            System.out.println("Прочитано " + writtenCities.size() + " міст з training_data.txt.");

            Set<CityWithCoordinates> translatedCities = readTranslatedCitiesFromFile(translatedFile);
            System.out.println("Прочитано " + translatedCities.size() + " міст з translated_all_ukr.txt.");
            uniqueCities.addAll(translatedCities); // Додаємо міста з translated_all_ukr.txt до uniqueCities

            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile, true), StandardCharsets.UTF_8));
            System.out.println("Буферний записувач ініціалізовано.");

            // Використання нового методу для читання ресурсів
            readAndProcessResourceFile(inputFile1, "\t", 2, true);
            System.out.println("Обробка файлу " + inputFile1 + " завершена. Знайдено " + uniqueCities.size() + " унікальних міст.");
            readAndProcessResourceFile(inputFile2, "\t", 1, true);
            System.out.println("Обробка файлу " + inputFile2 + " завершена. Знайдено " + uniqueCities.size() + " унікальних міст.");
            readAndProcessResourceFile(inputFileUA2, "\t", 1, true);
            System.out.println("Обробка файлу " + inputFileUA2 + " завершена. Знайдено " + uniqueCities.size() + " унікальних міст.");
            readAndProcessResourceFile(inputFileUA3, "\t", 1, true);
            System.out.println("Обробка файлу " + inputFileUA3 + " завершена. Знайдено " + uniqueCities.size() + " унікальних міст.");


            if (checkJsonResourceExists(inputFile4)) {
                readAndProcessJsonResourceFile(inputFile4);
                System.out.println("Обробка файлу " + inputFile4 + " завершена. Знайдено " + uniqueCities.size() + " унікальних міст.");
            } else {
                System.out.println("Попередження: JSON-файл " + inputFile4 + " не знайдено або він порожній.");
            }

            // Додаємо всі міста з uniqueCities до writtenCities
            // Цей рядок раніше був до циклу запису, і це було правильно.
            // Щоб уникнути подвійного запису, ми додаємо до writtenCities тільки те, що було в ньому до цього моменту.
            // uniqueCities містить нові міста, які ми хочемо записати.
            // Ми будемо додавати їх до writtenCities під час запису, щоб уникнути дублікатів у поточному сеансі.

            // Проходимося по uniqueCities і записуємо ті, яких ще немає у writtenCities
            for (CityWithCoordinates cityWithCoords : uniqueCities) {
                // Ця перевірка тепер критично важлива, оскільки uniqueCities містить також міста з translated_all_ukr.txt
                // і з файлу training_data.txt, прочитані на початку.
                if (!writtenCities.contains(cityWithCoords)) {
                    String city = cityWithCoords.getName();
                    String lat = cityWithCoords.getLat();
                    String lng = cityWithCoords.getLng();

                    if (lat != null && lng != null) {
                        writer.write("<START:location> " + city + " (" + lat + ", " + lng + ") <END>\n");
                    } else {
                        writer.write("<START:location> " + city + " <END>\n");
                    }
                    System.out.println("Записано місто: " + city + (lat != null && lng != null ? " (" + lat + ", " + lng + ")" : ""));
                    writtenCities.add(cityWithCoords); // Додаємо щойно записане місто до writtenCities
                } else {
                    System.out.println("Місто " + cityWithCoords.getName() + " вже записано.");
                }
            }


            System.out.println("Файл training_data.txt створено/оновлено успішно!");

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Помилка при обробці файлів: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Непередбачена помилка: " + e.getMessage());
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        System.out.println("<<< Завершення методу processFiles()");
    }

    // Змінений метод для читання файлів-ресурсів
    private Set<CityWithCoordinates> readCitiesFromFile(String filePath) throws IOException {
        Set<CityWithCoordinates> cities = new HashSet<>();
        // Для читання training_data.txt, який ми записуємо в тимчасову директорію,
        // ми продовжуємо використовувати прямий доступ до файлу
        File file = new File(filePath);
        if (!file.exists() || file.length() == 0) {
            System.out.println("Попередження: Вихідний файл " + filePath + " не знайдено або він порожній. Створюємо новий.");
            return cities; // Повертаємо порожній сет, якщо файл не існує
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) { // Додано StandardCharsets.UTF_8
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("<START:location>")) {
                    String city = line.substring("<START:location>".length(), line.indexOf("<END>")).trim();
                    String lat = null;
                    String lng = null;

                    int coordsStart = line.indexOf("(");
                    if (coordsStart != -1) {
                        int coordsEnd = line.indexOf(")", coordsStart);
                        if (coordsEnd != -1) {
                            String coords = line.substring(coordsStart + 1, coordsEnd).trim();
                            String[] coordsParts = coords.split(",");
                            if (coordsParts.length == 2) {
                                try {
                                    lat = coordsParts[0].trim();
                                    lng = coordsParts[1].trim();
                                } catch (NumberFormatException e) {
                                    System.err.println("Помилка парсингу координат у файлі " + filePath + ": " + coords);
                                }
                            }
                        }
                    }
                    cities.add(new CityWithCoordinates(city, lat, lng));
                }
            }
        }
        return cities;
    }


    // Змінений метод для читання файлів-ресурсів
    private Set<CityWithCoordinates> readTranslatedCitiesFromFile(String resourcePath) throws IOException {
        Set<CityWithCoordinates> cities = new HashSet<>();
        Resource resource = resourceLoader.getResource("classpath:" + resourcePath);
        if (!resource.exists() || resource.contentLength() == 0) {
            System.out.println("Попередження: Ресурс " + resourcePath + " не знайдено або він порожній.");
            return cities;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            Pattern pattern = Pattern.compile("<START:location>\\s*(.*?)\\s*\\((.*?),\\s*(.*?)\\)\\s*<END>");
            while ((line = reader.readLine()) != null) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    String city = matcher.group(1).trim();
                    String lat = matcher.group(2).trim();
                    String lng = matcher.group(3).trim();
                    cities.add(new CityWithCoordinates(city, lat, lng));
                } else if (line.startsWith("<START:location>")) {
                    String city = line.substring("<START:location>".length(), line.indexOf("<END>")).trim();
                    cities.add(new CityWithCoordinates(city, null, null));
                }
            }
        }
        return cities;
    }

    // Новий метод для читання вхідних ресурсних файлів (GG.txt, UA.txt тощо)
    private void readAndProcessResourceFile(String resourcePath, String delimiter, int columnIndex, boolean extractCoordinates) throws IOException {
        Resource resource = resourceLoader.getResource("classpath:" + resourcePath);
        if (!resource.exists() || resource.contentLength() == 0) {
            System.out.println("Попередження: Ресурс " + resourcePath + " не знайдено або він порожній.");
            return;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(delimiter);
                if (parts.length > columnIndex) {
                    String cityName = parts[columnIndex].trim();
                    String lat = null;
                    String lng = null;

                    if (extractCoordinates && parts.length > 6) {
                        lat = parts[4].trim();
                        lng = parts[5].trim();
                    }

                    CityWithCoordinates cityWithCoords = new CityWithCoordinates(cityName, lat, lng);
                    // uniqueCities.add(cityWithCoords); // Додаємо до uniqueCities незалежно від того, чи було вже записано
                    // Ми вже перевіряємо на дублікат при записі в файл. Тут просто додаємо для подальшої обробки.
                    // Якщо потрібно відфільтрувати їх раніше, тоді можна додати if (!writtenCities.contains(cityWithCoords))
                    uniqueCities.add(cityWithCoords);
                }
            }
        }
    }


    // Змінений метод для перевірки існування JSON-ресурсу
    private boolean checkJsonResourceExists(String resourcePath) {
        Resource resource = resourceLoader.getResource("classpath:" + resourcePath);
        try {
            return resource.exists() && resource.contentLength() > 0;
        } catch (IOException e) {
            System.err.println("Помилка при перевірці JSON-ресурсу " + resourcePath + ": " + e.getMessage());
            return false;
        }
    }

    // Змінений метод для читання та обробки JSON-ресурсу
    private void readAndProcessJsonResourceFile(String resourcePath) throws IOException {
        Resource resource = resourceLoader.getResource("classpath:" + resourcePath);
        if (!resource.exists() || resource.contentLength() == 0) {
            System.out.println("Попередження: JSON-ресурс " + resourcePath + " не знайдено або він порожній.");
            return;
        }

        // Читаємо вміст ресурсу
        StringBuilder contentBuilder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                contentBuilder.append(line);
            }
        }
        String content = contentBuilder.toString();


        if (content.trim().isEmpty()) {
            System.out.println("Попередження: JSON-ресурс " + resourcePath + " порожній.");
            return;
        }

        JSONArray regionsArray = new JSONArray(content);

        for (int i = 0; i < regionsArray.length(); i++) {
            JSONObject regionObject = regionsArray.getJSONObject(i);

            if (regionObject.has("center")) {
                String centerName = regionObject.getString("center").trim();
                CityWithCoordinates cityWithCoords = new CityWithCoordinates(centerName, null, null);
                // uniqueCities.add(cityWithCoords); // Додаємо до uniqueCities незалежно від того, чи було вже записано
                uniqueCities.add(cityWithCoords);
            }

            if (regionObject.has("cities")) {
                JSONArray citiesArray = regionObject.getJSONArray("cities");
                for (int j = 0; j < citiesArray.length(); j++) {
                    JSONObject cityObject = citiesArray.getJSONObject(j);
                    if (cityObject.has("name")) {
                        String cityName = cityObject.getString("name").trim();
                        String lat = cityObject.has("lat") ? cityObject.getString("lat").trim() : null;
                        String lng = cityObject.has("lng") ? cityObject.getString("lng").trim() : null;

                        CityWithCoordinates cityWithCoords = new CityWithCoordinates(cityName, lat, lng);
                        // uniqueCities.add(cityWithCoords); // Додаємо до uniqueCities незалежно від того, чи було вже записано
                        uniqueCities.add(cityWithCoords);
//                            if (lat != null && lng != null) {
//                                geoLocator.trainModel(cityName, lat, lng);
//                            }
                    }
                }
            }
        }
    }

    private static class CityWithCoordinates {
        private String name;
        private String lat;
        private String lng;

        public CityWithCoordinates(String name, String lat, String lng) {
            this.name = name;
            this.lat = lat;
            this.lng = lng;
        }

        public String getName() {
            return name;
        }

        public String getLat() {
            return lat;
        }

        public String getLng() {
            return lng;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CityWithCoordinates that = (CityWithCoordinates) o;
            // Важливо: порівнюємо без урахування регістру для імені, якщо це має сенс для міст
            // name.equalsIgnoreCase(that.name)
            return name.equals(that.name) && Objects.equals(lat, that.lat) && Objects.equals(lng, that.lng);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, lat, lng);
        }

        @Override
        public String toString() {
            return "<START:location> " + name + (lat != null && lng != null ? " (" + lat + ", " + lng + ")" : "") + " <END>";
        }
    }
}