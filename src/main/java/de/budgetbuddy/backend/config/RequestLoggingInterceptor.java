package de.budgetbuddy.backend.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatusCode;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;

@Slf4j
@Component
public class RequestLoggingInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        return HandlerInterceptor.super.preHandle(request, response, handler);
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable ModelAndView modelAndView) throws Exception {
        RequestLoggingInterceptor.logRequest(request, response);
        HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }

    public static void logRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getRequestURI();
        String requestMethod = request.getMethod();
        int status = response.getStatus();
        HttpStatusCode statusCode = HttpStatusCode.valueOf(status);
        MDC.setContextMap(Map.of(
                "response.status", String.valueOf(status),
                "request.method", requestMethod,
                "request.ip", request.getRemoteHost(),
                "request.path", path,
                "request.query", request.getQueryString(),
                "request.body", getBody(request),
                "request.header.authorization", request.getHeader("authorization")
        ));
        String msg = "Request {} {} {}";
        if (statusCode.is4xxClientError()) {
            log.warn(msg, requestMethod, status, path);
        } else if (statusCode.is5xxServerError()) {
            log.error(msg, requestMethod, status, path);
        } else {
            log.info(msg, requestMethod, status, path);
        }
        MDC.clear();
    }

    public static String getBody(HttpServletRequest request) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        BufferedReader bufferedReader = null;

        try {
            InputStream inputStream = request.getInputStream();
            if (inputStream != null) {
                bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                char[] charBuffer = new char[128];
                int bytesRead = -1;
                while ((bytesRead = bufferedReader.read(charBuffer)) > 0) {
                    stringBuilder.append(charBuffer, 0, bytesRead);
                }
            } else {
                stringBuilder.append("");
            }
        } catch (IOException ex) {
            throw ex;
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (IOException ex) {
                    throw ex;
                }
            }
        }

        return stringBuilder.toString();
    }
}