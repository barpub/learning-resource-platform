package com.example.platform.config;

import com.example.platform.service.UserService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {
    private final UserService userService;

    public DataSeeder(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void run(String... args) {
        userService.ensureSeedUser("admin", "admin123456", "admin@example.com", "ADMIN");
        userService.ensureSeedUser("user", "user123456", "user@example.com", "USER");
    }
}
