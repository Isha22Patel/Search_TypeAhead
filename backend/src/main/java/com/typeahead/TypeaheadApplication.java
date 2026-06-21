package com.typeahead;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Search Typeahead System.
 *
 * Architecture:
 * ┌──────────────────────────────────────────────────────────┐
 * │  React Frontend (Vite :5173)                             │
 * │      ↓ GET /suggest?q=  ↓ POST /search                  │
 * │  Spring Boot Backend (:8080)                             │
 * │      ↓ ConsistentHashRouter → LocalCacheNode (3 nodes)   │
 * │      ↓ Cache miss → PostgreSQL (Docker :5432)            │
 * │      ↓ POST /search → BatchWriteService buffer           │
 * │      ↓ @Scheduled flush → PostgreSQL UPSERT              │
 * └──────────────────────────────────────────────────────────┘
 */
@SpringBootApplication
@EnableScheduling
public class TypeaheadApplication {

    public static void main(String[] args) {
        SpringApplication.run(TypeaheadApplication.class, args);
    }
}
