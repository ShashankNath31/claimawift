package com.claimswift.notificationservice.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import org.slf4j.MDC;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StandardResponse<T> {
    private String code;
    private String message;
    private T data;
    private String requestId;

    public static <T> StandardResponse<T> success(String message, T data) {
        return StandardResponse.<T>builder()
                .code("SUCCESS")
                .message(message)
                .data(data)
                .requestId(currentRequestId())
                .build();
    }

    public static <T> StandardResponse<T> success(T data) {
        return success("Success", data);
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

    public static <T> StandardResponse<T> error(String message, String errorCode, T data, int statusCode) {
        return error(message, errorCode, data);
    }

    private static String currentRequestId() {
        return MDC.get("correlationId");
    }
}
