package com.example.demo.word;

import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
@Component
public class CityRootWordDetector {
    private final Logger logger = LoggerFactory.getLogger(CityRootWordDetector.class);
    private final MorphologyAnalyzer morphologyAnalyzer;
    private final LevenshteinDistance levenshteinDistance = new LevenshteinDistance();
    private final int LEVENSHTEIN_THRESHOLD = 2; // Більш жорсткий поріг для міст

    private final List<String> CITY_SUFFIXES = Arrays.asList("ський", "ська", "ське", "ці", "чани", "чанка", "щина", "щинський", "щинська", "щинське");
    private final List<String> CITY_ROOT_EXCEPTIONS = Arrays.asList("київ", "львів", "харків"); // Словник винятків

    @Autowired
    public CityRootWordDetector(MorphologyAnalyzer morphologyAnalyzer) {
        this.morphologyAnalyzer = morphologyAnalyzer;
    }

    public Set<String> getRootWords(String cityName) {
        Set<String> rootWords = new HashSet<>();
        String root = morphologyAnalyzer.getRoot(cityName);

        if (root != null) {
            rootWords.add(root);
        }

        // Додаємо правила для типових змін коренів у географічних назвах
        if (root != null) {
            rootWords.addAll(generateCityVariants(root));
        }

        // Використовуємо Левенштейна для виявлення схожих коренів
        Set<String> refinedRoots = new HashSet<>();
        for (String existingRoot : rootWords) {
            for (String otherRoot : rootWords) {
                if (!existingRoot.equals(otherRoot)) {
                    int distance = levenshteinDistance.apply(existingRoot, otherRoot);
                    if (distance <= LEVENSHTEIN_THRESHOLD) {
                        refinedRoots.add(existingRoot);
                        refinedRoots.add(otherRoot);
                    }
                }
            }
        }
        rootWords.addAll(refinedRoots);

        logger.info("Вихід з CityRootWordDetector для міста {}", cityName); // Додано лог
        return rootWords;
    }

    private Set<String> generateCityVariants(String root) {
        Set<String> variants = new HashSet<>();

        // Типові голосні заміни в географічних назвах
        if (root.contains("о")) {
            variants.add(root.replace("о", "а"));
        }
        if (root.contains("и")) {
            variants.add(root.replace("и", "і"));
        }

        // Типові суфікси міст: Київ → Київський, Львів → Львівський
        variants.add(root + "ський");
        variants.add(root + "ська");
        variants.add(root + "ське");
        variants.add(root + "ці");
        variants.add(root + "ці");
        variants.add(root + "чани");
        variants.add(root + "чанка");
        variants.add(root + "щина");
        variants.add(root + "щинський");
        variants.add(root + "щинська");
        variants.add(root + "щинське");

        // Інші варіанти
        if (root.endsWith("ів")) {
            variants.add(root.replace("ів", "ова"));
        }
        if (root.endsWith("ськ")) {
            variants.add(root + "ий");
        }
        if (root.endsWith("е")) {
            variants.add(root.replace("е", "я"));
        }
        if (root.endsWith("я")) {
            variants.add(root.replace("я", "є"));
        }

        // Додаємо варіанти зі словника
        for (String exception : CITY_ROOT_EXCEPTIONS) {
            if (root.equalsIgnoreCase(exception)) {
                for (String suffix : CITY_SUFFIXES) {
                    variants.add(root + suffix);
                }
            }
        }

        return variants;
    }
}