package com.filemind.activity.controller;

import com.filemind.activity.dto.ActivityItemResponse;
import com.filemind.activity.service.ActivityService;
import com.filemind.common.response.ApiResponse;
import com.filemind.common.security.AuthenticatedUserService;
import com.filemind.collaboration.service.DocumentAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/activity")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;
    private final AuthenticatedUserService authenticatedUserService;
    private final DocumentAccessService documentAccessService;

    @GetMapping
    public ApiResponse<List<ActivityItemResponse>> workspace() {
        var user = authenticatedUserService.getCurrentUser();
        List<String> documentIds = documentAccessService.listAccessibleDocuments(user).stream().map(document -> document.getId()).toList();
        return ApiResponse.success("Activity ready", activityService.recentWorkspaceActivity(user, documentIds));
    }

    @GetMapping("/recent")
    public ApiResponse<List<ActivityItemResponse>> recent() {
        return workspace();
    }
}
