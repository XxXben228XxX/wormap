package com.example.demo.word;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CityRepository extends JpaRepository<City, Long> {
    Logger logger = LoggerFactory.getLogger(CityRepository.class);

    Optional<City> findByName(String name);

    Optional<City> findByLatitudeAndLongitude(Double latitude, Double longitude);
    Optional<City> findByNameAndLatitudeAndLongitude(String name, Double latitude, Double longitude);

}