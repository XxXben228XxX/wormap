package com.example.demo.word;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

@Service
public class CityService {

    private final Logger logger = LoggerFactory.getLogger(CityService.class);
    private final CityRepository cityRepository;
    private final String LANG_UK_API_URL = "https://api.lang.org.ua/morph/analyze";
    private final NominatimService nominatimService;
    private final CityRootWordDetector cityRootWordDetector;
    private final CityVariantService cityVariantService;

    @Autowired
    public CityService(CityRepository cityRepository, NominatimService nominatimService, CityRootWordDetector cityRootWordDetector, CityVariantService cityVariantService) {
        this.cityRepository = cityRepository;
        this.nominatimService = nominatimService;
        this.cityRootWordDetector = cityRootWordDetector;
        this.cityVariantService = cityVariantService;
    }

    public Optional<City> saveCity(String name, Double latitude, Double longitude) {
        logger.info("Attempting to save city: {} (lat: {}, lon: {})", name, latitude, longitude);

        try {
            if (latitude != null && longitude != null) {
                Optional<City> existingCity = cityRepository.findByLatitudeAndLongitude(latitude, longitude);
                if (existingCity.isPresent()) {
                    logger.info("City already exists with latitude and longitude: {}", existingCity.get().getName());
                    return existingCity;
                }
            } else {
                Optional<City> existingCity = cityRepository.findByName(name);
                if (existingCity.isPresent()) {
                    logger.info("City already exists with name: {}", existingCity.get().getName());
                    return existingCity;
                }
            }

            City city = new City();
            city.setName(name);
            city.setLatitude(latitude);
            city.setLongitude(longitude);
            City savedCity = cityRepository.save(city);
            logger.info("City saved successfully: {}", savedCity.getName());

            // Отримуємо та зберігаємо однокореневі варіанти назв з Nominatim API
            nominatimService.saveAlternativeNames(savedCity, name);

            return Optional.of(savedCity);
        } catch (Exception e) {
            logger.error("Error saving city: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private List<String> generateMorphologicalVariants(String name) {
        logger.info("Generating morphological variants for: {}", name);
        List<String> variants = new ArrayList<>();
        variants.add(name);
        variants.add(name.toLowerCase());
        variants.add(name.toUpperCase());

        variants.addAll(getMorphologicalVariantsFromLangApi(name));
        variants.addAll(getRelatedWordsFromLangApi(name));

        logger.debug("Generated variants: {}", variants);
        return variants;
    }

    private List<String> getMorphologicalVariantsFromLangApi(String name) {
        return getWordsFromLangApi(name, "");
    }

    private List<String> getRelatedWordsFromLangApi(String name) {
        return getWordsFromLangApi(name, ", \"type\": \"related\"");
    }

    private List<String> getWordsFromLangApi(String name, String type) {
        RestTemplate restTemplate = new RestTemplate();
        List<String> words = new ArrayList<>();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");

            String body = "{ \"text\": \"" + name + "\", \"lang\": \"uk\"" + type + " }";
            HttpEntity<String> entity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(LANG_UK_API_URL, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                String jsonResponse = response.getBody();
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode rootNode = objectMapper.readTree(jsonResponse);

                String nodeName = type.isEmpty() ? "variants" : "related";
                if (rootNode.has(nodeName)) {
                    JsonNode wordsNode = rootNode.get(nodeName);
                    if (wordsNode.isArray()) {
                        for (JsonNode wordNode : wordsNode) {
                            words.add(wordNode.asText());
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error fetching words from Lang API: {}", e.getMessage());
        }

        logger.debug("Words from Lang API: {}", words);
        return words;
    }

    public void processTrainingData(String trainingDataPath) {
        logger.info("Processing training data from: {}", trainingDataPath);
        try (BufferedReader reader = new BufferedReader(new FileReader(trainingDataPath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("<START:location>")) {
                    String cityName = line.substring("<START:location>".length(), line.indexOf("<END>")).trim();
                    saveCity(cityName, null, null);
                }
            }
        } catch (IOException e) {
            logger.error("Error processing training data: {}", e.getMessage());
        }
    }

    public Optional<City> getCityByName(String name) {
        logger.info("Getting city by name: {}", name);
        return cityRepository.findByName(name);
    }
}