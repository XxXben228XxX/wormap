package com.example.demo.mod;

import org.languagetool.AnalyzedTokenReadings;
import org.languagetool.tagging.uk.UkrainianTagger;
import org.languagetool.tokenizers.uk.UkrainianWordTokenizer;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class LangUkExample {
    public static void main(String[] args) {
        // 1. Ініціалізуємо токенізатор і теггер
        UkrainianWordTokenizer tokenizer = new UkrainianWordTokenizer();
        UkrainianTagger tagger = new UkrainianTagger();

        // 2. Тестове речення
        String text = "Кіт швидко біжить.";

        // 3. Токенізація (розбиття на слова)
        List<String> tokens = tokenizer.tokenize(text);

        // 4. Аналіз (POS-теги та лематизація)
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("output.txt"))) {
            // Використовуємо tag() для аналізу токенів
            List<AnalyzedTokenReadings> analyzedTokens = tagger.tag(tokens);

            for (AnalyzedTokenReadings tokenReadings : analyzedTokens) {
                if (!tokenReadings.getToken().equals(" ")) { // Пропускаємо пробіли
                    String word = tokenReadings.getToken();
                    String tag = tokenReadings.getReadings().get(0).getPOSTag();
                    String lemma = tokenReadings.getReadings().get(0).getLemma(); // Отримання леми

                    System.out.println("Слово: " + word);
                    System.out.println("Теги: " + tag);
                    System.out.println("Лема: " + (lemma != null ? lemma : "немає леми"));
                    System.out.println();

                    // Записуємо у файл
                    writer.write("Слово: " + word + "\n");
                    writer.write("Теги: " + tag + "\n");
                    writer.write("Лема: " + (lemma != null ? lemma : "немає леми") + "\n");
                    writer.write("\n");
                }
            }
            System.out.println("Результати записано у файл output.txt");
        } catch (IOException e) {
            System.err.println("Помилка при обробці тексту: " + e.getMessage());
        }
    }
}
