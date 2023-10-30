package de.budgetbuddy.backend.config;

import de.budgetbuddy.backend.ApiResponse;
import de.budgetbuddy.backend.log.Log;
import de.budgetbuddy.backend.log.LogType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class CustomErrorController {

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<String> handleInternalServerError(Exception ex) {
        Log log = new Log(LogType.ERROR, "/error", ex.toString());
        System.out.println(log);

        return new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                ex.getMessage(),
                ex.toString());
    }
}
