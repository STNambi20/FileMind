package com.filemind.dashboard.controller;

import com.filemind.common.response.ApiResponse;
import com.filemind.common.security.AuthenticatedUserService;
import com.filemind.dashboard.dto.DashboardStatsResponse;
import com.filemind.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final AuthenticatedUserService authenticatedUserService;

    @GetMapping
    public ApiResponse<DashboardStatsResponse> dashboard() {
        return ApiResponse.success(
            "Dashboard ready",
            dashboardService.getDashboard(authenticatedUserService.getCurrentUser())
        );
    }
}
