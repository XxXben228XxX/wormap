package com.example.demo.word;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class MorphologyAnalyzer {

    private final Logger logger = LoggerFactory.getLogger(MorphologyAnalyzer.class);
    private final String LANG_UK_API_URL = "https://api.lang.org.ua/morph/analyze";

    public String getRoot(String word) {
        RestTemplate restTemplate = new RestTemplate();
        String root = null;

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");

            String body = "{ \"text\": \"" + word + "\", \"lang\": \"uk\" }";
            HttpEntity<String> entity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(LANG_UK_API_URL, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                String jsonResponse = response.getBody();
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode rootNode = objectMapper.readTree(jsonResponse);

                if (rootNode.isArray() && rootNode.size() > 0) {
                    JsonNode analysis = rootNode.get(0);
                    if (analysis.has("root")) {
                        root = analysis.get("root").asText();
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error fetching root from Lang API: {}", e.getMessage());
        }

        logger.debug("Root from Lang API: {}", root);
        return root;
    }
}