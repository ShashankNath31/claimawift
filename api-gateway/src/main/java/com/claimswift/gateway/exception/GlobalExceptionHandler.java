package com.claimswift.gateway.exception;

import com.claimswift.gateway.filter.CorrelationIdFilter;
import com.claimswift.gateway.util.ApiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Global Exception Handler for API Gateway
 */
@Slf4j
@Component
@Order(-1)
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        HttpStatus status = determineHttpStatus(ex);
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String requestId = exchange.getRequest().getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);
        String errorBody = createErrorResponse(ex, status, requestId);
        DataBuffer buffer = response.bufferFactory().wrap(errorBody.getBytes(StandardCharsets.UTF_8));

        log.error("Gateway error: {}", ex.getMessage(), ex);

        return response.writeWith(Flux.just(buffer));
    }

    private HttpStatus determineHttpStatus(Throwable ex) {
        if (ex instanceof ResponseStatusException) {
            return (HttpStatus) ((ResponseStatusException) ex).getStatusCode();
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private String createErrorResponse(Throwable ex, HttpStatus status, String requestId) {
        String message = ex.getMessage() != null ? ex.getMessage() : "Internal server error";
        ApiResponse<Void> payload = ApiResponse.error(message, status.name(), requestId);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException jsonEx) {
            log.error("Failed to serialize gateway error response", jsonEx);
            return "{\"code\":\"INTERNAL_ERROR\",\"message\":\"Unexpected error\",\"data\":null,\"requestId\":\"" + requestId + "\"}";
        }
    }
}
