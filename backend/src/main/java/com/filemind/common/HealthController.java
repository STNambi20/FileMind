package com.filemind.common;

import com.filemind.common.dto.HealthResponse;
import com.filemind.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public ApiResponse<HealthResponse> health() {
        return ApiResponse.success(
            "FileMind is ready",
            new HealthResponse("UP", "filemind-backend", "Core API is healthy")
        );
    }
}
