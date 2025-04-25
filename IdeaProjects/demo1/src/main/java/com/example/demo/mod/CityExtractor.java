package com.example.demo.mod;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.itextpdf.kernel.pdf.canvas.parser.listener.SimpleTextExtractionStrategy;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CityExtractor {

    public static void main(String[] args) {
        String pdfFilePath = "Etymolohichnyi_slovnyk_toponimiv_Ukrainy.pdf";
        String trainingDataPath = "src/main/resources/training_data.txt";
        String outputFilePath = "city_names.txt";

        List<String> cityNames = extractCityNamesFromPdf(pdfFilePath, trainingDataPath);

        if (cityNames != null) {
            writeCityNamesToFile(cityNames, outputFilePath);
            System.out.println("Назви міст видобуті та записані у файл: " + outputFilePath);
        }
    }

    public static List<String> extractCityNamesFromPdf(String pdfFilePath, String trainingDataPath) {
        List<String> cityNames = new ArrayList<>();
        List<String> trainingCities = readTrainingCities(trainingDataPath);

        try (PdfReader reader = new PdfReader(pdfFilePath);
             PdfDocument pdfDoc = new PdfDocument(reader)) {

            StringBuilder text = new StringBuilder();
            int pages = pdfDoc.getNumberOfPages();
            for (int i = 1; i <= pages; i++) {
                text.append(PdfTextExtractor.getTextFromPage(pdfDoc.getPage(i), new SimpleTextExtractionStrategy()));
            }

            // Регулярний вираз для видобування назв міст
            Pattern pattern = Pattern.compile("\\b[А-ЯІЇЄҐ][\\w'\\u0301\\-]+\\b");
            Matcher matcher = pattern.matcher(text.toString());

            while (matcher.find()) {
                String cityName = matcher.group();
                if (!trainingCities.contains(cityName)) { // Перевіряємо, чи немає назви в training_data.txt
                    cityNames.add(cityName);
                    System.out.println("Extracted city: " + cityName);
                }
            }

            System.out.println("Extracted text: " + text.toString().substring(0, Math.min(text.length(), 1000)));

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return cityNames;
    }

    public static List<String> readTrainingCities(String filePath) {
        List<String> cities = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                cities.add(line.trim());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return cities;
    }

    public static void writeCityNamesToFile(List<String> cityNames, String outputFilePath) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilePath))) {
            for (String cityName : cityNames) {
                writer.write(cityName);
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}