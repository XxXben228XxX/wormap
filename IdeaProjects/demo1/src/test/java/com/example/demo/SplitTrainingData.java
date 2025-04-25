package com.example.demo;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class SplitTrainingData {

    private static final String INPUT_FILE = "src/main/resources/training_data.txt";
    private static final String OUTPUT_PREFIX = "src/main/resources/translation_parts/part_";
    private static final int LINES_PER_PART = 90;

    public static void main(String[] args) {
        try (BufferedReader reader = new BufferedReader(new FileReader(INPUT_FILE))) {
            String line;
            int partNumber = 1;
            int lineNumber = 0;
            PrintWriter writer = null;

            while ((line = reader.readLine()) != null) {
                if (lineNumber % LINES_PER_PART == 0) {
                    if (writer != null) {
                        writer.close();
                    }
                    writer = new PrintWriter(new FileWriter(OUTPUT_PREFIX + partNumber + ".txt"));
                    System.out.println("Створення файлу: " + OUTPUT_PREFIX + partNumber + ".txt");
                    partNumber++;
                }
                writer.println(line);
                lineNumber++;
            }

            if (writer != null) {
                writer.close();
            }
            System.out.println("Розбиття файлу завершено.");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}