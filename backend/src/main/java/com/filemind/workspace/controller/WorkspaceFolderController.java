package com.filemind.workspace.controller;
import com.filemind.common.response.ApiResponse;
import com.filemind.common.security.AuthenticatedUserService;
import com.filemind.workspace.service.WorkspaceFolderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/workspace/folders") @RequiredArgsConstructor
public class WorkspaceFolderController {
 private final WorkspaceFolderService folders; private final AuthenticatedUserService auth;
 @GetMapping public ApiResponse<List<Map<String,Object>>> list(){return ApiResponse.success("Folders ready",folders.list(auth.getCurrentUser()));}
 @GetMapping("/requests") public ApiResponse<List<Map<String,Object>>> requests(){return ApiResponse.success("Access requests ready",folders.requests(auth.getCurrentUser()));}
 @PostMapping public ApiResponse<Map<String,Object>> create(@RequestBody Map<String,Object> body){return ApiResponse.success("Folder created",folders.create(auth.getCurrentUser(),(String)body.get("name"),Boolean.TRUE.equals(body.get("public"))));}
 @PatchMapping("/{id}/visibility") public ApiResponse<Map<String,Object>> visibility(@PathVariable String id,@RequestBody Map<String,Object> body){return ApiResponse.success("Folder visibility updated",folders.setPublic(auth.getCurrentUser(),id,Boolean.TRUE.equals(body.get("public"))));}
 @PostMapping("/{id}/collaborators") public ApiResponse<Map<String,Object>> share(@PathVariable String id,@RequestBody Map<String,String> body){return ApiResponse.success("Folder access updated",folders.share(auth.getCurrentUser(),id,body.get("email"),body.get("permission")));}
 @PostMapping("/{id}/documents") public ApiResponse<Map<String,Object>> addDocument(@PathVariable String id,@RequestBody Map<String,String> body){return ApiResponse.success("Document added to folder",folders.addDocument(auth.getCurrentUser(),id,body.get("documentId")));}
 @PostMapping("/requests/{accessId}/respond") public ApiResponse<Void> respond(@PathVariable String accessId,@RequestBody Map<String,Boolean> body){folders.respond(auth.getCurrentUser(),accessId,Boolean.TRUE.equals(body.get("accept")));return ApiResponse.success("Access request handled",null);}
 @PatchMapping("/{folderId}/collaborators/{accessId}") public ApiResponse<Map<String,Object>> permission(@PathVariable String folderId,@PathVariable String accessId,@RequestBody Map<String,String> body){return ApiResponse.success("Folder access updated",folders.updatePermission(auth.getCurrentUser(),folderId,accessId,body.get("permission")));}
 @DeleteMapping("/{folderId}/collaborators/{accessId}") public ApiResponse<Void> remove(@PathVariable String folderId,@PathVariable String accessId){folders.remove(auth.getCurrentUser(),folderId,accessId);return ApiResponse.success("Folder access removed",null);}
}
