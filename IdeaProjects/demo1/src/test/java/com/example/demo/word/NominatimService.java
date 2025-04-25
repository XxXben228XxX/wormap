package com.example.demo.word;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.context.annotation.Lazy;

import java.net.URI;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class NominatimService {
    private static final Logger logger = LoggerFactory.getLogger(NominatimService.class);
    private final CityVariantService cityVariantService;
    private final RestTemplate restTemplate;
    private final CityRootWordDetector cityRootWordDetector;
    private final CityService cityService;

    private final ConcurrentMap<String, Set<String>> cache = new ConcurrentHashMap<>(); // Кеш для запитів

    private static final int BATCH_SIZE = 10; // Розмір пакету обробки

    public NominatimService(CityVariantService cityVariantService, RestTemplate restTemplate,
                            CityRootWordDetector cityRootWordDetector, @Lazy CityService cityService) { // Додано @Lazy
        this.cityVariantService = cityVariantService;
        this.restTemplate = restTemplate;
        this.cityRootWordDetector = cityRootWordDetector;
        this.cityService = cityService;
    }

    @Async
    public CompletableFuture<Set<String>> getAlternativeNames(String cityName) {
        if (cache.containsKey(cityName)) {
            logger.info("Використання кешу для міста: {}", cityName);
            return CompletableFuture.completedFuture(cache.get(cityName));
        }

        Set<String> alternativeNames = new HashSet<>();
        try {
            String normalizedCityName = normalizeCityName(cityName);
            String encodedCityName = UriComponentsBuilder.fromUriString(normalizedCityName).build().encode().toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "YourAppName/1.0 (your@email.com)");
            headers.set("Accept-Language", "en");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            URI url = UriComponentsBuilder.fromHttpUrl("https://nominatim.openstreetmap.org/search")
                    .queryParam("q", encodedCityName)
                    .queryParam("format", "json")
                    .queryParam("limit", 5)
                    .build()
                    .toUri();

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                JSONArray jsonArray = new JSONArray(response.getBody());

                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject jsonObject = jsonArray.getJSONObject(i);
                    String displayName = jsonObject.getString("display_name");
                    String type = jsonObject.optString("type", "");

                    if (type.equals("city") || type.equals("town") || type.equals("village")) {
                        alternativeNames.add(displayName.split(",")[0].trim());
                    }
                }

                cache.put(cityName, alternativeNames); // Кешуємо результати
                logger.info("Отримано альтернативні назви для міста: {}", cityName);
            } else {
                logger.error("Помилка отримання альтернативних назв для міста {}: {}", cityName, response.getStatusCode());
            }
        } catch (RestClientException e) {
            logger.error("Помилка запиту до Nominatim API для міста {}: {}", cityName, e.getMessage());
        } catch (Exception e) {
            logger.error("Невідома помилка для міста {}: {}", cityName, e.getMessage());
        }

        return CompletableFuture.completedFuture(alternativeNames);
    }

    private String normalizeCityName(String cityName) {
        String temp = Normalizer.normalize(cityName, Normalizer.Form.NFD);
        return Pattern.compile("\\p{InCombiningDiacriticalMarks}+").matcher(temp).replaceAll("");
    }

    public void saveAlternativeNames(City city, String cityName) {
        try {
            CompletableFuture<Set<String>> futureNames = getAlternativeNames(cityName);
            Set<String> alternativeNames = futureNames.join();
            Set<String> rootWords = cityRootWordDetector.getRootWords(cityName);
            Set<String> savedVariants = new HashSet<>();

            int savedVariantsCount = 0;
            for (String variant : alternativeNames) {
                Set<String> variantRootWords = cityRootWordDetector.getRootWords(variant);
                if (areRootWordsSimilar(rootWords, variantRootWords) && !savedVariants.contains(variant)) {
                    CityVariant cityVariant = new CityVariant();
                    cityVariant.setCity(city);
                    cityVariant.setVariant(variant);
                    cityVariantService.saveCityVariant(cityVariant);
                    savedVariants.add(variant);
                    savedVariantsCount++;
                }
            }

            logger.info("{} варіантів збережено для міста: {}", savedVariantsCount, cityName);
        } catch (Exception e) {
            logger.error("Помилка під час збереження варіантів для міста {}: {}", cityName, e.getMessage());
        }
    }

    public void processCities(List<String> cityNames) {
        List<List<String>> batches = partitionList(cityNames, BATCH_SIZE);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (List<String> batch : batches) {
            CompletableFuture<Void> batchFuture = CompletableFuture.runAsync(() -> {
                for (String cityName : batch) {
                    try {
                        City city = cityService.getCityByName(cityName)
                                .orElse(cityService.saveCity(cityName, null, null).orElse(null));
                        if (city != null) {
                            saveAlternativeNames(city, cityName);
                        }
                    } catch (Exception e) {
                        logger.error("Помилка обробки міста {}: {}", cityName, e.getMessage());
                    }
                }
            });

            futures.add(batchFuture);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    private List<List<String>> partitionList(List<String> list, int batchSize) {
        return new ArrayList<>(list.stream()
                .collect(Collectors.groupingBy(city -> list.indexOf(city) / batchSize))
                .values());
    }

    private boolean areRootWordsSimilar(Set<String> rootWords1, Set<String> rootWords2) {
        int commonRoots = 0;

        for (String root1 : rootWords1) {
            for (String root2 : rootWords2) {
                if (root1.equals(root2)) {
                    commonRoots++;
                }
            }
        }

        double similarity = (double) commonRoots / Math.max(rootWords1.size(), rootWords2.size());
        if (similarity > 0.5) {
            return true;
        }

        int maxDistance = 3;
        for (String rootWord1 : rootWords1) {
            for (String rootWord2 : rootWords2) {
                int distance = calculateLevenshteinDistance(rootWord1, rootWord2);
                if (distance <= maxDistance) {
                    return true;
                }
            }
        }

        return false;
    }

    private int calculateLevenshteinDistance(String x, String y) {
        int[][] dp = new int[x.length() + 1][y.length() + 1];

        for (int i = 0; i <= x.length(); i++) {
            for (int j = 0; j <= y.length(); j++) {
                if (i == 0) {
                    dp[i][j] = j;
                } else if (j == 0) {
                    dp[i][j] = i;
                } else {
                    dp[i][j] = Math.min(Math.min(dp[i - 1][j - 1] + (x.charAt(i - 1) == y.charAt(j - 1) ? 0 : 1), dp[i - 1][j] + 1), dp[i][j - 1] + 1);
                }
            }
        }

        return dp[x.length()][y.length()];
    }
}