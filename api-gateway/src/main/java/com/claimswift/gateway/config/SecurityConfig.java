package com.claimswift.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .exceptionHandling(exceptionHandlingSpec -> exceptionHandlingSpec
                        .authenticationEntryPoint((exchange, ex) -> {
                            var response = exchange.getResponse();
                            response.setStatusCode(HttpStatus.UNAUTHORIZED);
                            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                            response.getHeaders().remove(HttpHeaders.WWW_AUTHENTICATE);
                            DataBuffer buffer = response.bufferFactory().wrap(
                                    "{\"code\":\"UNAUTHORIZED\",\"message\":\"Authentication token is missing or invalid.\",\"data\":null}"
                                            .getBytes(StandardCharsets.UTF_8)
                            );
                            return response.writeWith(Mono.just(buffer));
                        })
                        .accessDeniedHandler((exchange, ex) -> {
                            var response = exchange.getResponse();
                            response.setStatusCode(HttpStatus.FORBIDDEN);
                            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                            response.getHeaders().remove(HttpHeaders.WWW_AUTHENTICATE);
                            DataBuffer buffer = response.bufferFactory().wrap(
                                    "{\"code\":\"ACCESS_DENIED\",\"message\":\"Access denied.\",\"data\":null}"
                                            .getBytes(StandardCharsets.UTF_8)
                            );
                            return response.writeWith(Mono.just(buffer));
                        })
                )
                .authorizeExchange(exchange -> exchange
                        .pathMatchers(
                                "/actuator/health",
                                "/actuator/info",
                                "/actuator/prometheus",
                                "/actuator/metrics/**",
                                "/fallback/**"
                        ).permitAll()
                        .pathMatchers("/ws/**").permitAll()
                        .pathMatchers(
                                "/api/auth/**",
                                "/api/claims/**",
                                "/api/documents/**",
                                "/api/assessments/**",
                                "/api/payments/**",
                                "/api/notifications/**",
                                "/api/reports/**"
                        ).permitAll()
                        .anyExchange().denyAll()
                )
                .build();
    }
}
