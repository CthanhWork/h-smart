package com.hsmart.admin.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private int status;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(HttpStatus status, String message, T data) {
        return ApiResponse.<T>builder()
                .status(status.value())
                .message(message)
                .data(data)
                .build();
    }

    public static ApiResponse<Void> error(HttpStatus status, String message) {
        return ApiResponse.<Void>builder()
                .status(status.value())
                .message(message)
                .data(null)
                .build();
    }
}
