package com.example.demo.newsmap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;
import com.example.demo.mod.NewsParser;
import com.example.demo.mod.GeoNamesToOpenNLP;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

@SpringBootApplication(exclude = {SecurityAutoConfiguration.class})
@EnableJpaRepositories(basePackages = {"com.example.demo.repository", "com.example.demo.model"})
@ComponentScan(basePackages = "com.example.demo")
@EntityScan(basePackages = {"com.example.demo.entity", "com.example.demo.model", "com.example.demo.word"})
@EnableScheduling
public class NewsmapApplication {

    public static void main(String[] args) {
        String classpath = System.getProperty("java.class.path");
        System.out.println("Classpath: " + classpath);
        SpringApplication.run(NewsmapApplication.class, args);
    }

    @Component
    public static class NewsParserScheduler {

        @Autowired
        private NewsParser newsParser;
        @Autowired
        private GeoNamesToOpenNLP geoNamesToOpenNLP;

        @Scheduled(fixedRate = 300000)
        public void runScheduledTask() {
            System.out.println("Запуск парсингу новин за розкладом...");
            newsParser.parseNews("https://www.ukr.net/news/russianaggression.html");
            System.out.println("Парсинг новин завершено.");

            System.out.println("Запуск GeoNamesToOpenNLP...");
            geoNamesToOpenNLP.processFiles();
            System.out.println("GeoNamesToOpenNLP завершено.");
        }
    }
}