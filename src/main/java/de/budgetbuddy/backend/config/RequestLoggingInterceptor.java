package de.budgetbuddy.backend.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import de.budgetbuddy.backend.log.LogType;
import de.budgetbuddy.backend.log.Logger;
import org.springframework.http.HttpStatusCode;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;

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
        // FIXME: Prevent sensitive-data to get logged
        String path = request.getRequestURI();
        int status = response.getStatus();
        Map<String, String> message = new HashMap<>();
        message.put("status", String.valueOf(status));
        message.put("method", request.getMethod());
        message.put("ip", request.getRemoteHost());
        message.put("path", path);
        message.put("query", request.getQueryString());
        message.put("body", getBody(request));
        message.put("authorization", request.getHeader("authorization"));

        HttpStatusCode statusCode = HttpStatusCode.valueOf(status);
        LogType logType = LogType.LOG;
        if (statusCode.is4xxClientError()) {
            logType = LogType.WARNING;
        } else if (statusCode.is5xxServerError()) {
            logType = LogType.ERROR;
        }

        Logger.getInstance().log("Backend", logType, path, message.toString());
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