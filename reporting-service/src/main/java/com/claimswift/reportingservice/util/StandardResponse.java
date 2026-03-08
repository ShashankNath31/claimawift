package com.claimswift.reportingservice.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.MDC;

/**
 * Standard API Response Wrapper
 * Used across all services for consistent response format
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StandardResponse<T> {
    
    private String code;
    private String message;
    private T data;
    private String requestId;

    public static <T> StandardResponse<T> success(T data) {
        return StandardResponse.<T>builder()
                .code("SUCCESS")
                .message("Success")
                .data(data)
                .requestId(currentRequestId())
                .build();
    }

    public static <T> StandardResponse<T> success(String message, T data) {
        return StandardResponse.<T>builder()
                .code("SUCCESS")
                .message(message)
                .data(data)
                .requestId(currentRequestId())
                .build();
    }

    public static <T> StandardResponse<T> error(String message) {
        return StandardResponse.<T>builder()
                .code("ERROR")
                .message(message)
                .requestId(currentRequestId())
                .build();
    }

    public static <T> StandardResponse<T> error(String message, String errorCode) {
        return error(message, errorCode, null);
    }

    public static <T> StandardResponse<T> error(String message, String errorCode, T data) {
        return StandardResponse.<T>builder()
                .code(errorCode == null || errorCode.isBlank() ? "ERROR" : errorCode)
                .message(message)
                .data(data)
                .requestId(currentRequestId())
                .build();
    }

    public static <T> StandardResponse<T> error(String message, String errorCode, String correlationId) {
        return StandardResponse.<T>builder()
                .code(errorCode == null || errorCode.isBlank() ? "ERROR" : errorCode)
                .message(message)
                .requestId(correlationId)
                .build();
    }

    public static <T> StandardResponse<T> error(String message, String errorCode, T data, int statusCode) {
        return error(message, errorCode, data);
    }

    private static String currentRequestId() {
        return MDC.get("correlationId");
    }
}
