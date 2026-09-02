package com.filemind.auth.controller;

import com.filemind.auth.dto.AuthResponse;
import com.filemind.auth.dto.RegisterRequest;
import com.filemind.auth.service.AuthService;
import com.filemind.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicBootstrapController {

    private final AuthService authService;

    @PostMapping("/bootstrap-admin")
    public ApiResponse<AuthResponse> bootstrapAdmin(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success("First admin account ready", authService.bootstrapAdmin(request));
    }
}
