package com.mmarushika.api_compatibility.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public Health health() {
        return new Health("UP");
    }

    public record Health(String status) {
    }
}
