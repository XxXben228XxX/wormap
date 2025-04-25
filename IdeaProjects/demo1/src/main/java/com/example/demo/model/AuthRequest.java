package com.example.demo.model;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

public class AuthRequest {
    private String username;
    private String password;

    // Getters and Setters
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
    @GetMapping("/login")
    public String loginPage(Model model) {
        return "login"; // Відображає login.html
    }

    @GetMapping("/auth/register")
    public String registerPage(Model model) {
        return "register"; // Відображає register.html
    }
}