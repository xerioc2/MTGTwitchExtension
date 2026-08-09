package com.mtgtwitch.extension.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class LocalBridgeWebConfig implements WebMvcConfigurer {

    private static final String[] ALLOWED_ORIGIN_PATTERNS = {
            "https://*.ext-twitch.tv",
            "http://localhost:[*]",
            "https://localhost:[*]",
            "http://127.0.0.1:[*]",
            "https://127.0.0.1:[*]"
    };

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOriginPatterns())
                .allowedMethods("GET", "POST", "PUT", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }

    static String[] allowedOriginPatterns() {
        return ALLOWED_ORIGIN_PATTERNS.clone();
    }
}
