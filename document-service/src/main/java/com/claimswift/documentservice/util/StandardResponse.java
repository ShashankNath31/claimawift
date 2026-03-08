package com.claimswift.documentservice.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import org.slf4j.MDC;

/**
 * Standard API Response Wrapper
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StandardResponse<T> {

    private String code;
    private String message;
    private T data;
    private String requestId;
    private String path;

    public static <T> StandardResponse<T> success(T data) {
        StandardResponse<T> response = new StandardResponse<>();
        response.setCode("SUCCESS");
        response.setMessage("Success");
        response.setData(data);
        response.setRequestId(currentRequestId());
        return response;
    }

    public static <T> StandardResponse<T> success(String message, T data) {
        StandardResponse<T> response = new StandardResponse<>();
        response.setCode("SUCCESS");
        response.setMessage(message);
        response.setData(data);
        response.setRequestId(currentRequestId());
        return response;
    }

    public static <T> StandardResponse<T> error(String message) {
        return error(message, "ERROR");
    }

    public static <T> StandardResponse<T> error(String message, String errorCode) {
        return error(message, errorCode, null);
    }

    public static <T> StandardResponse<T> error(String message, String errorCode, T data) {
        StandardResponse<T> response = new StandardResponse<>();
        response.setCode(errorCode == null || errorCode.isBlank() ? "ERROR" : errorCode);
        response.setMessage(message);
        response.setData(data);
        response.setRequestId(currentRequestId());
        return response;
    }

    public static <T> StandardResponse<T> errorWithPath(String message, String errorCode, String path) {
        StandardResponse<T> response = new StandardResponse<>();
        response.setCode(errorCode == null || errorCode.isBlank() ? "ERROR" : errorCode);
        response.setMessage(message);
        response.setRequestId(currentRequestId());
        response.setPath(path);
        return response;
    }

    public static <T> StandardResponse<T> errorWithPath(String message, String path) {
        return errorWithPath(message, "ERROR", path);
    }

    public static <T> StandardResponse<T> error(String message, String errorCode, T data, int statusCode) {
        return error(message, errorCode, data);
    }

    private static String currentRequestId() {
        return MDC.get("correlationId");
    }
}
