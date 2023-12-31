package de.budgetbuddy.backend.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.budgetbuddy.backend.ApiResponse;
import de.budgetbuddy.backend.config.RequestLoggingInterceptor;
import de.budgetbuddy.backend.user.User;
import de.budgetbuddy.backend.user.UserRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.common.lang.Nullable;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class AuthorizationInterceptor implements HandlerInterceptor {
    private final UserRepository userRepository;
    private final ObjectMapper objMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public AuthorizationInterceptor(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Important
     * The Authorization header should be structured as follows.
     * `Bearer: UUID.HASHED_PASSWORD`
     * The values are separated and then verified in the `AuthorizationInterceptor`. The current user for the session is then determined based on the UUID and set as the "user" session attribute.
     */
    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) throws Exception {
        PathMatcher pathMatcher = new AntPathMatcher();
        String path = request.getRequestURI();
        if (pathMatcher.match("/v1/auth/**", path)
            || request.getMethod().equals(HttpMethod.OPTIONS.toString())) {
            return true;
        }

        try {
            String authHeader = request.getHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer")) {
                handleUnauthorizedResponse(request, response, "No Bearer-Token was provided");
                return false;
            }

            AuthValues authValues = AuthorizationInterceptor.AuthValues
                    .extractToken(authHeader);
            if (authValues.getUuid() == null || authValues.getHashedPassword() == null) {
                handleUnauthorizedResponse(request, response, "Invalid Bearer-Token format");
                return false;
            }

            Optional<User> optAuthHeaderUser = userRepository
                    .findByUuidAndPassword(authValues.getUuid(), authValues.getHashedPassword());
            if (optAuthHeaderUser.isEmpty()) {
                handleUnauthorizedResponse(request, response, "Provided Bearer-Token is invalid");
                return false;
            }

            User authHeaderUser = optAuthHeaderUser.get();
            HttpSession session = request.getSession(true);
            session.setAttribute("user", objMapper.writeValueAsString(authHeaderUser));
            return true;
        } catch (IllegalArgumentException | JsonProcessingException ex) {
            handleErrorResponse(request, response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "internal-server-error", ex.getMessage());
            return false;
        }
    }

    @Override
    public void postHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            @Nullable ModelAndView modelAndView) throws Exception {
        HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            @Nullable Exception ex) throws Exception {
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }

    public static AuthValues retrieveTokenValue(String authorizationHeader) {
        String bearerTokenValue = authorizationHeader.substring("Bearer".length() + 1);
        int indexOfFirstDot = bearerTokenValue.indexOf(".");
        if (indexOfFirstDot == -1) return new AuthValues();

        UUID uuid = UUID.fromString(bearerTokenValue.substring(0, indexOfFirstDot));
        String hashedPassword = bearerTokenValue.substring(indexOfFirstDot + 1);
        return new AuthValues(uuid, hashedPassword);
    }

    @Data
    @AllArgsConstructor
    public static class AuthValues {
        private UUID uuid;
        private String hashedPassword;

        public AuthValues() {}

        public String getBearerToken() {
            return "Bearer " + uuid.toString() + "." + hashedPassword;
        }

        public static AuthValues extractToken(String authorizationHeader) {
            String[] headerParts = authorizationHeader.split(" ");
            if (headerParts.length != 2) throw new IllegalArgumentException("Invalid Authorization header format");
            String[] tokenParts = headerParts[1].split("\\.");;
            UUID uuid = UUID.fromString(tokenParts[0]);
            String hashedPassword = Arrays.stream(tokenParts, 1, tokenParts.length)
                    .collect(Collectors.joining("."));
            return new AuthValues(uuid, hashedPassword);
        }
    }

    private void handleUnauthorizedResponse(
            HttpServletRequest request,
            HttpServletResponse response,
            String errorMessage) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        ApiResponse<?> apiResponse = new ApiResponse<>(HttpServletResponse.SC_UNAUTHORIZED, errorMessage);
        response.getWriter().write(new ObjectMapper().writeValueAsString(apiResponse));
        RequestLoggingInterceptor.logRequest(request, response);
    }

    private void handleErrorResponse(
            HttpServletRequest request,
            HttpServletResponse response,
            int statusCode,
            String errorType,
            String errorMessage) throws IOException {
        response.setStatus(statusCode);
        response.setContentType("application/json");
        ApiResponse<String> apiResponse = new ApiResponse<>(statusCode, errorType, errorMessage);
        response.getWriter().write(new ObjectMapper().writeValueAsString(apiResponse));
        RequestLoggingInterceptor.logRequest(request, response);
    }

    public static boolean isValidUserSession(HttpSession session) {
        return session != null && session.getAttribute("user") != null;
    }

    public static Optional<User> getSessionUser(HttpSession session) throws JsonProcessingException {
        if (!AuthorizationInterceptor.isValidUserSession(session)) {
            return Optional.empty();
        }

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        User user = objectMapper.readValue(session.getAttribute("user").toString(), User.class);
        return Optional.ofNullable(user);
    }

    public static <T> ResponseEntity<ApiResponse<T>> noValidSessionResponse() {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<T>(HttpStatus.UNAUTHORIZED.value(), "No valid session found. Sign in first"));
    }
}