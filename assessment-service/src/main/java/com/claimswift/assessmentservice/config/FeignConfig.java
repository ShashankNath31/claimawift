package com.claimswift.assessmentservice.config;

import feign.Logger;
import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String authHeader = request.getHeader("Authorization");
                if (authHeader != null && !authHeader.isBlank()) {
                    requestTemplate.header("Authorization", authHeader);
                }

                String incomingCorrelationId = request.getHeader(CORRELATION_ID_HEADER);
                if (incomingCorrelationId != null && !incomingCorrelationId.isBlank()) {
                    requestTemplate.header(CORRELATION_ID_HEADER, incomingCorrelationId);
                }
            }

            String correlationIdFromMdc = MDC.get("correlationId");
            if (correlationIdFromMdc != null && !correlationIdFromMdc.isBlank()) {
                requestTemplate.header(CORRELATION_ID_HEADER, correlationIdFromMdc);
            }
        };
    }
}
