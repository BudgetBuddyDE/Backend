package de.budgetbuddy.backend;

import lombok.Data;

import java.util.ArrayList;

@Data
public class ApiResponse<T> {
    private int status;
    private String message;
    private T data;

    public ApiResponse(int status) {
        this.status = status;
    }

    public ApiResponse(String message, T data) {
        this.status = 200;
        this.message = message;
        this.data = data;
    }

    public ApiResponse(T data) {
        this.status = 200;
        this.data = data;
    }

    public ApiResponse(int status, T data) {
        this.status = status;
        this.data = data;
    }

    public ApiResponse(int status, String message) {
        this.status = status;
        this.message = message;
    }

    public ApiResponse(int status, String message, T data) {
        this.status = status;
        this.message = message;
        this.data = data;
    }
}