package com.filemind.admin.controller;

import com.filemind.admin.dto.AdminOverviewResponse;
import com.filemind.admin.dto.AdminUserResponse;
import com.filemind.admin.dto.UpdateUserRoleRequest;
import com.filemind.admin.service.AdminService;
import com.filemind.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/overview")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AdminOverviewResponse> overview() {
        return ApiResponse.success("Admin overview ready", adminService.getOverview());
    }

    @PatchMapping("/users/{userId}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AdminUserResponse> updateUserRole(@PathVariable String userId,
                                                        @Valid @RequestBody UpdateUserRoleRequest request) {
        return ApiResponse.success("User role updated", adminService.updateUserRole(userId, request.role()));
    }
}
