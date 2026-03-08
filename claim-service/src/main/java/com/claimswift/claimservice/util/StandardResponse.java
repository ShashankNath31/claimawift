package com.claimswift.claimservice.util;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.MDC;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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

    public static <T> StandardResponse<T> error(String message, int statusCode) {
        return error(message, statusCode, null);
    }

    public static <T> StandardResponse<T> error(String message, int statusCode, T data) {
        return error(message, mapStatusToCode(statusCode), data);
    }

    public static <T> StandardResponse<T> error(String message, String errorCode) {
        return error(message, errorCode, null, 400);
    }

//    public static <T> StandardResponse<T> error(String message, String errorCode, T data) {
//        return error(message, errorCode, data, 400);
//    }

    public static <T> StandardResponse<T> error(String message, String errorCode, T data, int statusCode) {
        return error(message, errorCode, data);
    }

    public static <T> StandardResponse<T> error(String message, String errorCode, T data) {
        return StandardResponse.<T>builder()
                .code(errorCode == null || errorCode.isBlank() ? "ERROR" : errorCode)
                .message(message)
                .data(data)
                .requestId(currentRequestId())
                .build();
    }

    private static String mapStatusToCode(int statusCode) {
        return switch (statusCode) {
            case 400 -> "BAD_REQUEST";
            case 401 -> "UNAUTHORIZED";
            case 403 -> "ACCESS_DENIED";
            case 404 -> "NOT_FOUND";
            case 409 -> "CONFLICT";
            case 422 -> "UNPROCESSABLE_ENTITY";
            case 500 -> "INTERNAL_ERROR";
            default -> "ERROR";
        };
    }

    private static String currentRequestId() {
        return MDC.get("correlationId");
    }
}
