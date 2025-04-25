package com.example.demo.word;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/cities")
public class CityController {
    @Autowired
    private CityService cityService;

    @PostMapping
    public ResponseEntity<City> addCity(@RequestParam String name) {
        Optional<City> city = cityService.saveCity(name, null, null);
        return city.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.badRequest().build());
    }

    @GetMapping("/{name}")
    public ResponseEntity<City> getCity(@PathVariable String name) {
        Optional<City> city = cityService.getCityByName(name);
        return city.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
    @PostMapping("/processTrainingData")
    public ResponseEntity<String> processTrainingData() {
        String trainingDataPath = "C:/Users/Den/IdeaProjects/demo1/src/main/resources/training_data.txt";
        cityService.processTrainingData(trainingDataPath);
        return ResponseEntity.ok("Дані з training_data.txt оброблено.");
    }
}
