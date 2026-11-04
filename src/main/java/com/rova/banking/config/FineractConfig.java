package com.rova.banking.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@ConditionalOnProperty(name = "fineract.enabled", havingValue = "true")
@EnableConfigurationProperties(FineractSettings.class)
@EnableScheduling
public class FineractConfig {}
