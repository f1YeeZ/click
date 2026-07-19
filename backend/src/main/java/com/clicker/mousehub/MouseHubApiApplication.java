package com.clicker.mousehub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MouseHubApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(MouseHubApiApplication.class, args);
    }
}
