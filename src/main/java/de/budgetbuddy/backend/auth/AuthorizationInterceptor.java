package de.budgetbuddy.backend.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.budgetbuddy.backend.ApiResponse;
import de.budgetbuddy.backend.user.User;
import de.budgetbuddy.backend.user.UserRepository;
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

import java.util.Optional;
import java.util.UUID;

@Component
public class AuthorizationInterceptor implements HandlerInterceptor {
    private final UserRepository userRepository;
    private final ObjectMapper objMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public AuthorizationInterceptor(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        PathMatcher pathMatcher = new AntPathMatcher();
        String path = request.getRequestURI();
        if (pathMatcher.match("/v1/auth/**", path)) {
            return true;
        }

        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer")) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                ApiResponse<?> apiResponse = new ApiResponse<>(HttpServletResponse.SC_UNAUTHORIZED, "No Bearer-Token we're provided");
                response.getWriter().write(new ObjectMapper().writeValueAsString(apiResponse));
                return false;
            }

            String bearerValue = authHeader.substring("Bearer".length() + 1);
            UUID uuid = UUID.fromString(bearerValue);
            Optional<User> optAuthHeaderUser = userRepository.findById(uuid);
            if (optAuthHeaderUser.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                ApiResponse<?> apiResponse = new ApiResponse<>(HttpServletResponse.SC_UNAUTHORIZED, "Provided Bearer-Token is invalid");
                response.getWriter().write(new ObjectMapper().writeValueAsString(apiResponse));
                return false;
            }

            User authHeaderUser = optAuthHeaderUser.get();
            HttpSession session = request.getSession(true);
            session.setAttribute("user", objMapper.writeValueAsString(authHeaderUser));
            return true;
        } catch (IllegalArgumentException | JsonProcessingException ex) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("application/json");
            ApiResponse<String> apiResponse = new ApiResponse<String>(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "internal-server-error", ex.getMessage());
            response.getWriter().write(new ObjectMapper().writeValueAsString(apiResponse));
            return false;
        }
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

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable ModelAndView modelAndView) throws Exception {
        HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }
}