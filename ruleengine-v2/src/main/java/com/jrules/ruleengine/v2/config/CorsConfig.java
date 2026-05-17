package com.jrules.ruleengine.v2.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.stream.Stream;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /**
     * Comma-separated list of extra allowed origins injected at runtime.
     * Set ALLOWED_ORIGINS env var on Render to your Vercel frontend URL.
     * e.g. ALLOWED_ORIGINS=https://minerva-studio.vercel.app
     */
    @Value("${ALLOWED_ORIGINS:}")
    private String allowedOriginsEnv;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] defaults = {
            "http://localhost:3000",
            "http://localhost:5173",
        };

        String[] extra = allowedOriginsEnv.isBlank()
                ? new String[0]
                : Arrays.stream(allowedOriginsEnv.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .toArray(String[]::new);

        String[] origins = Stream.concat(Arrays.stream(defaults), Arrays.stream(extra))
                .distinct()
                .toArray(String[]::new);

        registry.addMapping("/api/v2/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
