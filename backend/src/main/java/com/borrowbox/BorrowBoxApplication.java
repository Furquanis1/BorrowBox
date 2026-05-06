package com.borrowbox;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * BorrowBox Application Entry Point
 * 
 * This is the main class that starts the Spring Boot application.
 * The @SpringBootApplication annotation:
 * - Enables component scanning in this package and sub-packages
 * - Sets up Spring Boot configuration
 * - Enables auto-configuration
 */
@SpringBootApplication
public class BorrowBoxApplication {

    public static void main(String[] args) {
        // SpringApplication.run() starts the entire application
        SpringApplication.run(BorrowBoxApplication.class, args);
    }

}
