package com.aigreentick.services.template.config.client;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Configuration
@ConfigurationProperties(prefix = "messaging-service")
@Data
public class MessagingClientProperties {
    private String baseUrl;
}