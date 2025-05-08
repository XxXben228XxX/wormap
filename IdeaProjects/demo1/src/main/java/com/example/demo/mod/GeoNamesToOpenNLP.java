package com.example.demo.mod;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class GeoNamesToOpenNLP {

    @Autowired
    private GeminiGeoLocator geoLocator;

    private Set<CityWithCoordinates> uniqueCities = new HashSet<>();
    private Set<CityWithCoordinates> writtenCities = new HashSet<>();

    public void processFiles() {
        System.out.println(">>> Запуск методу processFiles()");
        String inputFile1 = "GG.txt";
        String inputFile2 = "UA.txt";
        String inputFileUA2 = "UA2.txt";
        String inputFileUA3 = "UA3.txt";
        String inputFile4 = "coordinates.json";
        String translatedFile = "translated_all_ukr.txt";
        String outputFile = "training_data.txt";

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

            readAndProcessFile(inputFile1, "\t", 2, true);
            System.out.println("Обробка файлу " + inputFile1 + " завершена. Знайдено " + uniqueCities.size() + " унікальних міст.");
            readAndProcessFile(inputFile2, "\t", 1, true);
            System.out.println("Обробка файлу " + inputFile2 + " завершена. Знайдено " + uniqueCities.size() + " унікальних міст.");
            readAndProcessFile(inputFileUA2, "\t", 1, true);
            System.out.println("Обробка файлу " + inputFileUA2 + " завершена. Знайдено " + uniqueCities.size() + " унікальних міст.");
            readAndProcessFile(inputFileUA3, "\t", 1, true);
            System.out.println("Обробка файлу " + inputFileUA3 + " завершена. Знайдено " + uniqueCities.size() + " унікальних міст.");

            if (checkJsonFileExists(inputFile4)) {
                readAndProcessJsonFile(inputFile4);
                System.out.println("Обробка файлу " + inputFile4 + " завершена. Знайдено " + uniqueCities.size() + " унікальних міст.");
            } else {
                System.out.println("Попередження: JSON-файл " + inputFile4 + " не знайдено або він порожній.");
            }

            // Додаємо всі міста з uniqueCities до writtenCities
            writtenCities.addAll(uniqueCities);
            System.out.println("Кількість міст у writtenCities перед записом: " + writtenCities.size());

            for (CityWithCoordinates cityWithCoords : uniqueCities) {
                String city = cityWithCoords.getName();
                String lat = cityWithCoords.getLat();
                String lng = cityWithCoords.getLng();

                if (!writtenCities.contains(cityWithCoords)) {
                    if (lat != null && lng != null) {
                        writer.write("<START:location> " + city + " (" + lat + ", " + lng + ") <END>\n");
                    } else {
                        writer.write("<START:location> " + city + " <END>\n");
                    }
                    System.out.println("Записано місто: " + city + (lat != null && lng != null ? " (" + lat + ", " + lng + ")" : ""));
                    writtenCities.add(cityWithCoords);
                } else {
                    System.out.println("Місто " + city + " вже записано.");
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

    private Set<CityWithCoordinates> readCitiesFromFile(String filePath) throws IOException {
        Set<CityWithCoordinates> cities = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
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

    private Set<CityWithCoordinates> readTranslatedCitiesFromFile(String filePath) throws IOException {
        Set<CityWithCoordinates> cities = new HashSet<>();
        org.springframework.core.io.ClassPathResource resource = new org.springframework.core.io.ClassPathResource(filePath);
        if (!resource.exists()) {
            System.err.println("Попередження: Ресурс " + filePath + " не знайдено.");
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

    private void readAndProcessFile(String inputFile, String delimiter, int columnIndex, boolean extractCoordinates) throws IOException {
        File file = new File(inputFile);
        if (!file.exists() || file.length() == 0) {
            System.out.println("Попередження: Файл " + inputFile + " не знайдено або він порожній.");
            return;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile), StandardCharsets.UTF_8))) {
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
                    if (!writtenCities.contains(cityWithCoords)) { // Перевіряємо на дублікат перед додаванням
                        uniqueCities.add(cityWithCoords);
                    }
                }
            }
        }
    }

    private boolean checkJsonFileExists(String inputFile) {
        File file = new File(inputFile);
        return file.exists() && file.length() > 0;
    }

    private void readAndProcessJsonFile(String inputFile) throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(inputFile)), StandardCharsets.UTF_8);

        if (content.trim().isEmpty()) {
            System.out.println("Попередження: JSON-файл " + inputFile + " порожній.");
            return;
        }

        JSONArray regionsArray = new JSONArray(content);

        for (int i = 0; i < regionsArray.length(); i++) {
            JSONObject regionObject = regionsArray.getJSONObject(i);

            if (regionObject.has("center")) {
                String centerName = regionObject.getString("center").trim();
                CityWithCoordinates cityWithCoords = new CityWithCoordinates(centerName, null, null);
                if (!writtenCities.contains(cityWithCoords)) { // Перевіряємо на дублікат перед додаванням
                    uniqueCities.add(cityWithCoords);
                }
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
                        if (!writtenCities.contains(cityWithCoords)) { // Перевіряємо на дублікат перед додаванням
                            uniqueCities.add(cityWithCoords);
//                            if (lat != null && lng != null) {
//                                geoLocator.trainModel(cityName, lat, lng);
//                            }
                        }
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