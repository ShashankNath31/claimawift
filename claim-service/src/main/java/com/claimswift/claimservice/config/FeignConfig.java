package com.claimswift.claimservice.config;

import feign.Logger;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

@Slf4j
@Configuration
public class FeignConfig {
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";


    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    @Bean
    public RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            String correlationId = org.slf4j.MDC.get("correlationId");
            if (correlationId != null) {
                requestTemplate.header(CORRELATION_ID_HEADER, correlationId);
            }

            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String authHeader = request.getHeader("Authorization");
                if (authHeader != null && !authHeader.isBlank()) {
                    requestTemplate.header("Authorization", authHeader);
                }

                if (correlationId == null || correlationId.isBlank()) {
                    String incomingCorrelationId = request.getHeader(CORRELATION_ID_HEADER);
                    if (incomingCorrelationId != null && !incomingCorrelationId.isBlank()) {
                        requestTemplate.header(CORRELATION_ID_HEADER, incomingCorrelationId);
                    }
                }
            }
            log.debug("Feign request to: {}", requestTemplate.url());
        };
    }

    @Bean
    public ErrorDecoder errorDecoder() {
        return new FeignErrorDecoder();
    }
}
