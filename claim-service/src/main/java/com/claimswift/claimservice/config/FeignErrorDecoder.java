package com.claimswift.claimservice.config;

import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FeignErrorDecoder implements ErrorDecoder {

    @Override
    public Exception decode(String methodKey, Response response) {
        log.error("Feign error in method {}: status {}", 
            methodKey, response.status());
        
        switch (response.status()) {
            case 404:
                return new RuntimeException("Service not found");
            case 500:
                return new RuntimeException("Internal server error in downstream service");
            default:
                return new RuntimeException("Error calling service: " + response.status());
        }
    }
}