package com.claimswift.claimservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

@FeignClient(name = "auth-service")
public interface AuthServiceClient {

    @GetMapping("/api/auth/me")
    Map<String, Object> getCurrentUser();

    @GetMapping("/api/auth/managers")
    Map<String, Object> getManagers();
}
