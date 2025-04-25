package com.example.demo.word;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CityVariantService {

    private final Logger logger = LoggerFactory.getLogger(CityVariantService.class);
    private final CityVariantRepository cityVariantRepository;

    @Autowired
    public CityVariantService(CityVariantRepository cityVariantRepository) {
        this.cityVariantRepository = cityVariantRepository;
    }

    public CityVariant saveCityVariant(CityVariant cityVariant) {
        logger.info("Saving city variant: {} for city: {}", cityVariant.getVariant(), cityVariant.getCity().getName());
        CityVariant savedVariant = cityVariantRepository.save(cityVariant);
        logger.info("City variant saved successfully: {}", savedVariant); // Додано логування
        return savedVariant;
    }
}