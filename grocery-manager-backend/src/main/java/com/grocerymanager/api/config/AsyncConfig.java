package com.grocerymanager.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Configuration class to enable asynchronous method execution
 * for better email sending performance.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
    // The @EnableAsync annotation enables Spring's ability
    // to run @Async methods in a background thread pool
}