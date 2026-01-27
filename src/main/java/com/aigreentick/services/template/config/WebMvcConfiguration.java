package com.aigreentick.services.template.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * Web MVC Configuration for serving static media files.
 * 
 * This configuration allows Spring to serve files directly from the filesystem
 * without going through a controller for better performance.
 */
@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {

    @Value("${media.upload.directory:uploads/media}")
    private String uploadDirectory;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Convert relative path to absolute path
        String absolutePath = Paths.get(uploadDirectory).toAbsolutePath().toUri().toString();
        
        // Map /uploads/media/** to the physical directory
        registry.addResourceHandler("/uploads/media/**")
                .addResourceLocations(absolutePath)
                .setCachePeriod(604800); // Cache for 7 days (in seconds)
    }
}