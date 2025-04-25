package com.example.demo.word;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.CompletableFuture;

@Component
public class CityParser {
    private static final Logger logger = LoggerFactory.getLogger(CityParser.class);
    private final CityService cityService;
    private final CityRepository cityRepository;
    private final NominatimService nominatimService;
    private final CityVariantService cityVariantService;
    private final CityRootWordDetector cityRootWordDetector;
    private static boolean hasParsedTrainingData = false;

    public CityParser(CityService cityService, CityRepository cityRepository, NominatimService nominatimService, CityVariantService cityVariantService, CityRootWordDetector cityRootWordDetector) {
        this.cityService = cityService;
        this.cityRepository = cityRepository;
        this.nominatimService = nominatimService;
        this.cityVariantService = cityVariantService;
        this.cityRootWordDetector = cityRootWordDetector;
    }

    public void parseCitiesFromFile(String filePath) {
        logger.info("parseCitiesFromFile() called with path: {}", filePath);

        String text;
        try {
            text = readTextFromFile(filePath);
        } catch (IOException e) {
            logger.error("Failed to read file: {}", filePath, e);
            return;
        }

        parseCitiesFromText(text);
    }

    public void parseCitiesFromText(String text) {
        hasParsedTrainingData = false; // Додайте цей рядок
        logger.info("parseCitiesFromText() called. hasParsedTrainingData: {}", hasParsedTrainingData);
        logger.info("parseCitiesFromText() called.");

        if (hasParsedTrainingData) {
            logger.info("Training data already parsed. Skipping.");
            return;
        }

        if (text == null || text.isEmpty()) {
            logger.warn("Provided text for city parsing is empty or null.");
            return;
        }

        logger.info("Text to parse:\n{}", text.substring(0, Math.min(text.length(), 200)) + "..."); // Виводимо перші 200 символів тексту

        int lineCount = countLinesInFile("C:/Users/Den/IdeaProjects/demo1/src/main/resources/training_data.txt");
        logger.info("Number of lines in training_data.txt: {}", lineCount);

        Set<CityWithCoordinates> citiesWithCoordinates = extractCitiesWithCoordinates(text);
        logger.info("Number of cities extracted: {}", citiesWithCoordinates.size());

        if (citiesWithCoordinates.isEmpty()) {
            logger.warn("No cities extracted from text.");
        } else {
            logger.info("Cities extracted successfully.");
        }

        saveCitiesToDatabase(citiesWithCoordinates);

        hasParsedTrainingData = true;
        logger.info("City parsing from text completed.");
    }

    private Set<CityWithCoordinates> extractCitiesWithCoordinates(String text) {
        Set<CityWithCoordinates> citySet = new HashSet<>();
        Pattern pattern = Pattern.compile("<START:location>\\s*(.+?)\\s*\\(([-+]?\\d+\\.\\d+),\\s*([-+]?\\d+\\.\\d+)\\)\\s*<END>");
        Matcher matcher = pattern.matcher(text);

        int cityCount = 0;

        logger.debug("Текст для аналізу:\n{}", text); // Додаємо логування тексту

        while (matcher.find()) {
            logger.debug("Знайдено відповідність: {}", matcher.group(0)); // Додаємо логування кожної знайденої відповідності

            String cityName = matcher.group(1).trim();
            double latitude = Double.parseDouble(matcher.group(2).trim());
            double longitude = Double.parseDouble(matcher.group(3).trim());



            CityWithCoordinates cityWithCoordinates = new CityWithCoordinates(cityName, latitude, longitude);
            citySet.add(cityWithCoordinates);

            cityCount++;
            // logger.debug("Знайдено місто: {}", cityName);
        }

        logger.info("Витягнуто {} міст.", cityCount);
        return citySet;
    }

    private void saveCitiesToDatabase(Set<CityWithCoordinates> cities) {
        int savedCitiesCount = 0;
        for (CityWithCoordinates cityWithCoordinates : cities) {
            saveCityToDatabase(cityWithCoordinates);
            savedCitiesCount++;
        }
        logger.info("{} cities saved to database.", savedCitiesCount);
    }

    private void saveCityToDatabase(CityWithCoordinates cityWithCoordinates) {
        String cityName = cityWithCoordinates.getCityName();
        Double latitude = cityWithCoordinates.getLatitude();
        Double longitude = cityWithCoordinates.getLongitude();

        if (cityName != null && latitude != null && longitude != null) {
            if (cityRepository.findByNameAndLatitudeAndLongitude(cityName, latitude, longitude).isPresent()) {
                logger.info("City already exists in database: {} ({}, {})", cityName, latitude, longitude);
            } else {
                City city = new City();
                city.setName(cityName);
                city.setLatitude(latitude);
                city.setLongitude(longitude);
                cityRepository.save(city);
                logger.info("Saved city to database: {} ({}, {})", cityName, latitude, longitude);

                // Отримуємо та зберігаємо варіанти назв з Nominatim API
                CompletableFuture<Set<String>> futureNames = nominatimService.getAlternativeNames(cityName);
                Set<String> alternativeNames = futureNames.join(); // Блокуємо, доки не отримаємо результат

                int savedVariantsCount = 0;
                for (String variant : alternativeNames) {
                    CityVariant cityVariant = new CityVariant();
                    cityVariant.setCity(city);
                    cityVariant.setVariant(variant);
                    cityVariantService.saveCityVariant(cityVariant);
                    savedVariantsCount++;

                    // Виявляємо однокореневі слова
                    Set<String> rootWords = cityRootWordDetector.getRootWords(variant);
                    for (String rootWord : rootWords) {
                        CityVariant rootVariant = new CityVariant();
                        rootVariant.setCity(city);
                        rootVariant.setVariant(rootWord);
                        cityVariantService.saveCityVariant(rootVariant);
                        savedVariantsCount++;
                    }
                }
                logger.info("{} variants saved for city: {}", savedVariantsCount, cityName);
            }
        } else {
            logger.warn("City data is incomplete: cityName={}, latitude={}, longitude={}", cityName, latitude, longitude);
        }
    }

    private int countLinesInFile(String filePath) {
        int lineCount = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            while (reader.readLine() != null) {
                lineCount++;
            }
        } catch (IOException e) {
            logger.error("Error counting lines in file: {}", e.getMessage(), e);
        }
        return lineCount;
    }

    public void updateCitiesFromFile(String filePath) {
        logger.info("updateCitiesFromFile() called with path: {}", filePath);

        String text;
        try {
            text = readTextFromFile(filePath);
        } catch (IOException e) {
            logger.error("Failed to read file: {}", filePath, e);
            return;
        }

        Set<CityWithCoordinates> updatedCities = extractCitiesWithCoordinates(text);
        updateCitiesInDatabase(updatedCities);
    }

    private void updateCitiesInDatabase(Set<CityWithCoordinates> updatedCities) {
        int updatedCitiesCount = 0;
        int addedCitiesCount = 0;
        for (CityWithCoordinates updatedCity : updatedCities) {
            Optional<City> existingCityOptional = cityRepository.findByNameAndLatitudeAndLongitude(
                    updatedCity.getCityName(), updatedCity.getLatitude(), updatedCity.getLongitude());

            if (existingCityOptional.isPresent()) {
                City existingCity = existingCityOptional.get();

                if (!existingCity.getLatitude().equals(updatedCity.getLatitude()) ||
                        !existingCity.getLongitude().equals(updatedCity.getLongitude())) {

                    existingCity.setLatitude(updatedCity.getLatitude());
                    existingCity.setLongitude(updatedCity.getLongitude());
                    cityRepository.save(existingCity);
                    updatedCitiesCount++;
                }
            } else {
                City city = new City();
                city.setName(updatedCity.getCityName());
                city.setLatitude(updatedCity.getLatitude());
                city.setLongitude(updatedCity.getLongitude());
                cityRepository.save(city);
                addedCitiesCount++;
            }
        }
        logger.info("{} cities updated, {} cities added.", updatedCitiesCount, addedCitiesCount);
        logger.info("City update completed.");
    }

    public String readTextFromFile(String filePath) throws IOException {
        StringBuilder contentBuilder = new StringBuilder();
        int wordCount = 0;
        boolean allWordsRead = true;

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String sCurrentLine;
            while ((sCurrentLine = br.readLine()) != null) {
                contentBuilder.append(sCurrentLine).append("\n");
                String[] words = sCurrentLine.split("\\s+");
                wordCount += words.length;
                logger.debug("Прочитаний рядок: {}", sCurrentLine); // Додати лог
            }
        } catch (IOException e) {
            allWordsRead = false;
            logger.error("Помилка читання файлу: {}", e.getMessage(), e);
            throw e;
        }

        String content = contentBuilder.toString();
        logger.info("Кількість слів, прочитаних з файлу: {}", wordCount);
        logger.info("Всі слова прочитані з файлу: {}", allWordsRead);
        return content;
    }

    private static class CityWithCoordinates {
        private final String cityName;
        private final Double latitude;
        private final Double longitude;

        public CityWithCoordinates(String cityName, Double latitude, Double longitude) {
            this.cityName = cityName;
            this.latitude = latitude;
            this.longitude = longitude;
        }

        public String getCityName() {
            return cityName;
        }

        public Double getLatitude() {
            return latitude;
        }

        public Double getLongitude() {
            return longitude;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            CityWithCoordinates that = (CityWithCoordinates) o;

            if (!cityName.equals(that.cityName)) return false;
            if (!latitude.equals(that.latitude)) return false;
            return longitude.equals(that.longitude);
        }

        @Override
        public int hashCode() {
            int result = cityName.hashCode();
            result = 31 * result + latitude.hashCode();
            result = 31 * result + longitude.hashCode();
            return result;
        }
    }
}