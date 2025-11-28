package com.ayush.controller;



import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    @GetMapping
    public String home() {
        return "Welcome to the app";
    }
    @GetMapping("/api")
    public String secure() {
        return "Welcome to the app Secure";
    }
}
