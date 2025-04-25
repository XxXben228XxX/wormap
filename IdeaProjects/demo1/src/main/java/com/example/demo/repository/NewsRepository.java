package com.example.demo.repository;

import com.example.demo.entity.News;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NewsRepository extends JpaRepository<News, Long> {
    // Custom queries can be added here if needed
    List<News> findByTitleContaining(String keyword); // Метод для пошуку новин за ключовим словом у заголовку
}