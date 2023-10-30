package de.budgetbuddy.backend.config;

import de.budgetbuddy.backend.auth.AuthorizationInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final AuthorizationInterceptor authorizationInterceptor = new AuthorizationInterceptor();
    private final RequestLoggingInterceptor requestLoggingInterceptor = new RequestLoggingInterceptor();

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Enable auth via session for endpoints
        registry
                .addInterceptor(authorizationInterceptor)
                .order(1);

        registry
                .addInterceptor(requestLoggingInterceptor)
                .order(2);
    }
}