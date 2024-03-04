package de.budgetbuddy.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.Map;

@Slf4j
@Configuration
@EnableWebMvc
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry
                .addMapping("/**")
                .allowedOriginPatterns("http://localhost:5173", "http://localhost:3000", "https://*budget-buddy.de*")
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(true);

        MDC.setContextMap(Map.of(
                "mapping", "/**",
                "allowed-origins", String.join(",", List.of("http://localhost:5173", "http://localhost:3000", "https://*budget-buddy.de*")),
                "allowed-methods", "*",
                "allowed-headers", "*",
                "allow-credentials", "true"
        ));
        log.info("CorsRegistry was configured");
        MDC.clear();
    }
}