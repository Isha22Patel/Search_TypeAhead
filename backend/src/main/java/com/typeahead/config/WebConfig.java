package com.typeahead.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

/**
 * CORS configuration for local development.
 *
 * Allows the React Vite frontend (http://localhost:5173) to make
 * cross-origin requests to the Spring Boot backend (http://localhost:8080).
 *
 * In production: restrict allowedOrigins to your actual domain.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
            .allowedOrigins(
                "http://localhost:5173",  // Vite dev server
                "http://localhost:3000"   // Alternative dev port
            )
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(false)
            .maxAge(3600);
    }
}
