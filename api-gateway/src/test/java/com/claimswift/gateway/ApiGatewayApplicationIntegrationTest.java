package com.claimswift.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.config.enabled=false",
                "spring.cloud.config.fail-fast=false",
                "spring.cloud.discovery.enabled=false",
                "eureka.client.enabled=false",
                "jwt.secret=ClaimSwiftSecretKeyForJwtSigningAndVerificationMustBeAtLeast256BitsLong",
                "management.endpoints.web.exposure.include=health,info,metrics,prometheus",
                "management.endpoint.prometheus.enabled=true",
                "management.prometheus.metrics.export.enabled=true"
        }
)
@AutoConfigureWebTestClient
class ApiGatewayApplicationIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void healthEndpointShouldBeAccessible() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void prometheusEndpointShouldBeAccessible() {
        webTestClient.mutate()
                .responseTimeout(Duration.ofSeconds(30))
                .build()
                .get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk();
    }
}
