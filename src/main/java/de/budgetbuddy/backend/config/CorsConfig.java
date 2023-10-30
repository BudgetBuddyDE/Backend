package de.budgetbuddy.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableWebMvc
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry
                .addMapping("/**")
                .allowedOriginPatterns("http://127.0.0.1*", "http://*localhost*", "https://budget-buddy.de", "https://*budget-buddy.de*")
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}