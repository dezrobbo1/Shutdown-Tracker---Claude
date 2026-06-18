package com.shutdowntracker.api;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class VersionController {

    @GetMapping("/version")
    public Map<String, String> version() {
        return Map.of(
                "service", "shutdown-tracker-api",
                "status", "placeholder"
        );
    }
}
