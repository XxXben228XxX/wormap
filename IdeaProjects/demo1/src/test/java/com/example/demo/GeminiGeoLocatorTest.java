package com.example.demo;

import com.example.demo.mod.GeminiGeoLocator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@SpringBootApplication
@ComponentScan("com.example.demo.mod")
public class GeminiGeoLocatorTest implements CommandLineRunner {

    @Autowired
    private GeminiGeoLocator geoLocator;

    private static final String OUTPUT_FILE = "gemini_locator_test_results.txt";

    public static void main(String[] args) {
        SpringApplication.run(GeminiGeoLocatorTest.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        try (PrintWriter writer = new PrintWriter(new FileWriter(OUTPUT_FILE, true))) {
            writer.println("--------------------------------------------------");
            writer.println("Результати тестування GeminiGeoLocator");
            writer.println("Дата та час: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            writer.println("--------------------------------------------------");

            writer.println("\nТестування україномовних назв:");
            writer.println("--------------------------");
            testLocation("Київ", writer);
            testLocation("Львів", writer);
            testLocation("Чернівці", writer);
            testLocation("Якась неіснуюча локація", writer);

            writer.println("\nТестування україномовних назв (з формату <START:location>):");
            writer.println("-------------------------------------------------------");
            testLocation("Zabaryshka (48.98333, 25.26667)", writer);
            testLocation("Nadiyivka (48.22889, 34.75361)", writer);
            testLocation("Tykhonovychi (51.93908, 32.17206)", writer);
            testLocation("Dankivka (49.10533, 29.31711)", writer);
            testLocation("Zhurzhevka (48.63744, 27.22067)", writer);

            writer.println("\nТестування англомовних назв (з формату <START:location>):");
            writer.println("-------------------------------------------------------");
            testLocation("Lyskonohy (49.76979, 35.64255)", writer);
            testLocation("Mykolaivka (50.46598, 30.0274)", writer);
            testLocation("London", writer);
            testLocation("New York", writer);

            writer.println("\nТестування тексту з назвою локації:");
            writer.println("----------------------------------");
            testLocation("Сьогодні відбулася подія в місті Київ.", writer);
            testLocation("The meeting will be held in London tomorrow.", writer);
            testLocation("Новини з Надіївки повідомляють про...", writer);
            testLocation("Біля Забаришки сталася аварія.", writer);

            writer.println("\n--------------------------------------------------");
            System.out.println("Результати тестування записано у файл: " + OUTPUT_FILE);

        } catch (IOException e) {
            System.err.println("Помилка запису у файл: " + e.getMessage());
        }
    }

    private void testLocation(String text, PrintWriter writer) {
        String location = geoLocator.getLocationFromText(text);
        String output = "Текст: \"" + text + "\" -> Локація: \"" + location + "\"";
        writer.println(output);
        System.out.println(output); // Також виводимо в консоль
    }
}
