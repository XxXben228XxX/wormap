package com.example.demo.mod;

import jakarta.annotation.PostConstruct;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.util.Span;
import org.languagetool.AnalyzedTokenReadings;
import org.languagetool.tagging.uk.UkrainianTagger;
import org.languagetool.tokenizers.uk.UkrainianWordTokenizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.apache.commons.text.similarity.LevenshteinDistance;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class GeminiGeoLocator {
    private static final Logger logger = LoggerFactory.getLogger(GeminiGeoLocator.class);
    private NameFinderME locationFinder;
    private UkrainianWordTokenizer tokenizer;
    private UkrainianTagger tagger;
    private static LevenshteinDistance levenshteinDistance = new LevenshteinDistance();
    private Set<String> knownCities = new HashSet<>();

    private static final int LEVENSHTEIN_THRESHOLD = 2;
    private static final List<String> CITY_SUFFIXES = Arrays.asList(
            "ський", "ська", "ське", "ці", "чани", "чанка", "щина", "щинський", "щинська", "щинське",
            "град", "поль", "піль", "бург", "слав", "жанськ", "янськ",
            "ів", "їв", "ово", "єво", "ино", "іно",
            "ськ", "к", "ж", "р", "т", "м", "н", "с", "я", "е", "и", "і", "ї", "у", "є", "й" // Однобуквені закінчення - дуже обережно!
    );
    private static final List<String> CITY_ROOT_EXCEPTIONS = Arrays.asList(
            "київ", "львів", "харків", "одеса", "дніпро", "донецьк", "запоріжжя", "кривий ріг",
            "миколаїв", "вінниця", "полтава", "чернігів", "черкаси", "суми", "житомир",
            "хмельницький", "рівне", "кропивницький", "івано-франківськ", "тернопіль",
            "луцьк", "чернівці", "ужгород", "сімферополь", "севастополь", // Важливі великі міста
            "бровари", "буча", "ірпінь", "вишгород", "славутич", // Міста Київської області (з контексту)
            "маріуполь", "херсон", "мелітополь", "бердянськ" // Міста, які часто згадуються у новинах
    );

    @PostConstruct
    public void init() {
        try {
            ClassPathResource modelResource = new ClassPathResource("models/en-ner-location.bin");
            if (modelResource.exists()) {
                try (InputStream modelIn = modelResource.getInputStream()) {
                    TokenNameFinderModel model = new TokenNameFinderModel(modelIn);
                    locationFinder = new NameFinderME(model);
                    logger.info("Модель для розпізнавання географічних назв успішно завантажена.");
                }
            } else {
                logger.error("❌ Не знайдено моделі OpenNLP за шляхом: models/en-ner-location.bin");
            }
        } catch (IOException e) {
            logger.error("Помилка завантаження моделі OpenNLP: {}", e.getMessage());
        }

        tokenizer = new UkrainianWordTokenizer();
        tagger = new UkrainianTagger();
        loadKnownCities();
        trainModel();
    }

    private void loadKnownCities() {
        knownCities.clear(); // Очищаємо множину перед завантаженням
        ClassPathResource citiesResource = new ClassPathResource("training_data.txt");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(citiesResource.getInputStream()))) {
            String line;
            Pattern pattern = Pattern.compile("<START:location>\\s*([^\\(]*?)\\s*(?:\\(.*\\))?\\s*<END>");
            while ((line = reader.readLine()) != null) {
                Matcher matcher = pattern.matcher(line);
                while (matcher.find()) {
                    knownCities.add(simplifyLemma(matcher.group(1).trim().toLowerCase()));
                }
            }
            logger.info("Завантажено {} відомих міст.", knownCities.size());
        } catch (IOException e) {
            logger.error("Помилка завантаження списку міст: {}", e.getMessage());
        }
    }

    public String getLocationFromText(String newsText) {
        List<AnalyzedTokenReadings> lemmatizedTokensReadings = lemmatizeText(newsText);
        if (lemmatizedTokensReadings == null) {
            return "Не вдалося лематизувати текст.";
        }

        List<String> lemmas = new ArrayList<>();
        for (AnalyzedTokenReadings atr : lemmatizedTokensReadings) {
            if (!atr.getReadings().isEmpty() && atr.getReadings().get(0).getLemma() != null) {
                lemmas.add(atr.getReadings().get(0).getLemma().toLowerCase()); // Отримуємо лему та приводимо до нижнього регістру
            } else {
                lemmas.add(atr.getToken().toLowerCase()); // Якщо лема відсутня або null, використовуємо оригінальний токен
            }
        }

        Set<String> foundCities = new HashSet<>();

        // Шукаємо збіги з відомими містами серед лем
        for (String lemma : lemmas) {
            String simplifiedLemma = simplifyLemma(lemma);
            if (knownCities.contains(simplifiedLemma)) {
                foundCities.add(lemma); // Зберігаємо оригінальну лему
            }
        }

        if (!foundCities.isEmpty()) {
            String result = String.join(", ", foundCities);
            logger.info("Знайдено відоме місто (за лемами): {}", result);
            return result;
        }

        // Якщо не знайдено за лемами, використовуємо модель OpenNLP на оригінальному тексті
        if (locationFinder != null) {
            String[] originalTokens = SimpleTokenizer.INSTANCE.tokenize(newsText);
            Span[] nameSpans = locationFinder.find(originalTokens);
            if (nameSpans != null && nameSpans.length > 0) {
                List<String> locations = new ArrayList<>();
                for (Span span : nameSpans) {
                    StringBuilder location = new StringBuilder();
                    for (int i = span.getStart(); i < span.getEnd(); i++) {
                        location.append(originalTokens[i]).append(" ");
                    }
                    locations.add(simplifyLemma(location.toString().trim()));
                }
                String result = String.join(", ", locations);
                logger.info("Знайдено місце за допомогою OpenNLP: {}", result);
                return result;
            }
        }

        logger.warn("Місце не знайдено.");
        return "Місце не знайдено.";
    }

    public void trainModel() {
        Set<String> uniqueLocations = new HashSet<>();
        int totalLines = 0;

        ClassPathResource trainingDataResource = new ClassPathResource("training_data.txt");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(trainingDataResource.getInputStream()))) {
            String line;
            Pattern pattern = Pattern.compile("<START:location>\\s*([^\\(]*?)\\s*(?:\\(.*\\))?\\s*<END>");
            while ((line = reader.readLine()) != null) {
                totalLines++;
                Matcher matcher = pattern.matcher(line);
                while (matcher.find()) {
                    uniqueLocations.add(simplifyLemma(matcher.group(1).trim().toLowerCase()));
                }
            }
            logger.info("Проаналізовано {} рядків у файлі training_data.txt.", totalLines);
            logger.info("Знайдено {} унікальних назв локацій.", uniqueLocations.size());

        } catch (IOException e) {
            logger.error("Помилка при читанні файлу training_data.txt: {}", e.getMessage());
        }
    }

    public List<AnalyzedTokenReadings> lemmatizeText(String text) {
        List<String> tokens = tokenizer.tokenize(text);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("lemmatized_words.log", true))) {
            List<AnalyzedTokenReadings> analyzedTokens = tagger.tag(tokens);
            for (AnalyzedTokenReadings tokenReadings : analyzedTokens) {
                writer.write(tokenReadings.getToken() + " -> " + tokenReadings.getReadings().get(0).getLemma() + "\n");
            }
            return analyzedTokens;
        } catch (IOException e) {
            logger.error("Помилка при лематизації тексту: {}", e.getMessage());
            return null;
        }
    }

    private String simplifyLemma(String lemma) {
        lemma = lemma.trim().toLowerCase();
        if (lemma.endsWith("щина")) {
            lemma = lemma.substring(0, lemma.length() - 4);
        } else if (lemma.endsWith("ська область")) {
            lemma = lemma.substring(0, lemma.length() - 12);
        }

        for (String suffix : CITY_SUFFIXES) {
            if (lemma.endsWith(suffix)) {
                lemma = lemma.substring(0, lemma.length() - suffix.length());
                break;
            }
        }

        for (String exception : CITY_ROOT_EXCEPTIONS) {
            if (lemma.equalsIgnoreCase(exception)) {
                return lemma;
            }
        }

        return lemma;
    }
}