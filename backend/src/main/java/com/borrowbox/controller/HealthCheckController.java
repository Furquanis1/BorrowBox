package com.borrowbox.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Health Check Controller
 * 
 * This is a simple REST controller that provides a health check endpoint.
 * It's useful for:
 * - Verifying the API is running
 * - Load balancers checking if the service is up
 * - Monitoring tools checking service status
 * 
 * @RestController: Marks this class as a REST API controller
 *                  Spring automatically converts return values to JSON
 * @RequestMapping: Maps all endpoints in this controller to /api/health
 */
@RestController
@RequestMapping("/api/health")
public class HealthCheckController {

    /**
     * Simple health check endpoint
     * 
     * @GetMapping: This method responds to HTTP GET requests
     * @return: A ResponseEntity containing a JSON object with status info
     * 
     * When you visit http://localhost:8080/api/health, you'll see:
     * {
     *   "status": "UP",
     *   "message": "BorrowBox API is running",
     *   "timestamp": "2026-05-02T10:30:15.123456"
     * }
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> healthCheck() {
        // Create a map (dictionary) to hold our response
        Map<String, Object> response = new HashMap<>();
        
        // Add data to the response
        response.put("status", "UP");
        response.put("message", "BorrowBox API is running");
        response.put("timestamp", LocalDateTime.now());
        
        // Return HTTP 200 OK with the response body
        return ResponseEntity.ok(response);
    }

}
