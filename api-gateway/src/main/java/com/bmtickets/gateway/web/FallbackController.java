package com.bmtickets.gateway.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/** Circuit-breaker fallback: graceful, consistent problem+json instead of a raw 5xx/timeout. */
@RestController
public class FallbackController {

    @RequestMapping("/fallback/{service}")
    public Mono<ResponseEntity<Map<String, Object>>> fallback(@PathVariable String service) {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header("Retry-After", "5")
                .body(Map.of(
                        "title", "Service Unavailable",
                        "status", 503,
                        "code", "DEPENDENCY_UNAVAILABLE",
                        "detail", service + " is temporarily unavailable. Please retry shortly.")));
    }
}
