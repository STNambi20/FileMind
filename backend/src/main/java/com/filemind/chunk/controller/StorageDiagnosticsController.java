package com.filemind.chunk.controller;

import com.filemind.chunk.dto.StorageCleanupResponse;
import com.filemind.chunk.dto.StorageDiagnosticsResponse;
import com.filemind.chunk.service.StorageDiagnosticsService;
import com.filemind.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/storage")
@RequiredArgsConstructor
public class StorageDiagnosticsController {

    private final StorageDiagnosticsService storageDiagnosticsService;

    @GetMapping("/diagnostics")
    public ApiResponse<StorageDiagnosticsResponse> diagnostics() {
        return ApiResponse.success("Storage diagnostics ready", storageDiagnosticsService.diagnostics());
    }

    @PostMapping("/cleanup")
    public ApiResponse<StorageCleanupResponse> cleanup() {
        return ApiResponse.success("Storage cleanup completed", storageDiagnosticsService.cleanupUnreferencedChunks());
    }
}
