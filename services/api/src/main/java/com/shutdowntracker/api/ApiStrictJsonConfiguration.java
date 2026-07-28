package com.shutdowntracker.api;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Deliberate API-wide fail-closed JSON boundary.
 *
 * <p>All request DTOs reject unknown properties, duplicate properties, and numeric enum aliases so
 * no endpoint can silently accept a second or unsupported representation of authority-bearing input.
 */
@Configuration(proxyBeanMethods = false)
public class ApiStrictJsonConfiguration {

    @Bean
    Jackson2ObjectMapperBuilderCustomizer strictApiJson() {
        return builder -> builder.featuresToEnable(
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS,
                JsonParser.Feature.STRICT_DUPLICATE_DETECTION
        );
    }
}
