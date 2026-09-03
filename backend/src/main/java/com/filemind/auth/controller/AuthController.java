package com.filemind.auth.controller;

import com.filemind.auth.dto.AuthResponse;
import com.filemind.auth.dto.LoginRequest;
import com.filemind.auth.dto.RegisterRequest;
import com.filemind.auth.service.AuthService;
import com.filemind.common.response.ApiResponse;
import com.filemind.common.security.AuthenticatedUserService;
import com.filemind.user.dto.CurrentUserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthenticatedUserService authenticatedUserService;

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success("Account created", authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success("Signed in", authService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> me() {
        return ApiResponse.success(
            "Account loaded",
            authService.currentUser(authenticatedUserService.getCurrentUser())
        );
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.success("Signed out", null);
    }
}
