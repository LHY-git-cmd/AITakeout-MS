package com.sky.controller.admin;

import com.sky.annotation.RequireAdminPermission;
import com.sky.dto.KnowledgeBaseDTO;
import com.sky.entity.*;
import com.sky.enumeration.AdminPermission;
import com.sky.result.Result;
import com.sky.service.UserPublicKnowledgeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/** 管理端用户公共知识的独立内容导入与发布 API。 */
@RestController
@RequestMapping("/admin/agent/public-knowledge")
@RequiredArgsConstructor
public class UserPublicKnowledgeController {
    private final UserPublicKnowledgeService service;

    @GetMapping("/bases")
    @RequireAdminPermission(AdminPermission.PUBLIC_KB_READ)
    public Result<List<AgentKnowledgeBase>> bases() {
        return Result.success(service.listBases());
    }

    @PostMapping("/bases")
    @RequireAdminPermission(AdminPermission.PUBLIC_KB_EDIT)
    public Result<AgentKnowledgeBase> createBase(@Valid @RequestBody KnowledgeBaseDTO body) {
        return Result.success(service.createBase(body));
    }

    @PutMapping("/bases/{kbId}")
    @RequireAdminPermission(AdminPermission.PUBLIC_KB_EDIT)
    public Result<Void> updateBase(@PathVariable String kbId,
                                   @Valid @RequestBody KnowledgeBaseDTO body) {
        service.updateBase(kbId, body);
        return Result.success();
    }

    @PostMapping("/bases/{kbId}/archive")
    @RequireAdminPermission(AdminPermission.PUBLIC_KB_PUBLISH)
    public Result<Void> archiveBase(@PathVariable String kbId) {
        service.archiveBase(kbId);
        return Result.success();
    }

    @PostMapping("/bases/{kbId}/purge")
    @RequireAdminPermission(AdminPermission.PUBLIC_KB_PUBLISH)
    public Result<Void> purgeBase(@PathVariable String kbId, @RequestBody Map<String, Object> body) {
        service.purgeBase(kbId, String.valueOf(body.getOrDefault("confirmation_name", "")));
        return Result.success();
    }

    @GetMapping("/bases/{kbId}/documents")
    @RequireAdminPermission(AdminPermission.PUBLIC_KB_READ)
    public Result<List<AgentKnowledgeDocument>> documents(@PathVariable String kbId) {
        return Result.success(service.listDocuments(kbId));
    }

    @PostMapping(value = "/bases/{kbId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequireAdminPermission(AdminPermission.PUBLIC_KB_EDIT)
    public Result<AgentKnowledgeDocument> upload(
            @PathVariable String kbId,
            @RequestParam(defaultValue = "GENERAL") String category,
            @RequestPart("file") MultipartFile file) {
        return Result.success(service.upload(kbId, category, file));
    }

    @PostMapping(value = "/documents/{documentId}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequireAdminPermission(AdminPermission.PUBLIC_KB_EDIT)
    public Result<AgentKnowledgeDocument> uploadVersion(
            @PathVariable String documentId,
            @RequestParam(defaultValue = "GENERAL") String category,
            @RequestPart("file") MultipartFile file) {
        return Result.success(service.uploadVersion(documentId, category, file));
    }

    @PostMapping("/documents/{documentId}/reindex")
    @RequireAdminPermission(AdminPermission.PUBLIC_KB_EDIT)
    public Result<AgentKnowledgeIndexTask> reindex(@PathVariable String documentId) {
        return Result.success(service.reindex(documentId));
    }

    @GetMapping("/index-tasks/{taskId}")
    @RequireAdminPermission(AdminPermission.PUBLIC_KB_READ)
    public Result<AgentKnowledgeIndexTask> indexTask(@PathVariable String taskId) {
        return Result.success(service.getIndexTask(taskId));
    }

    @DeleteMapping("/documents/{documentId}")
    @RequireAdminPermission(AdminPermission.PUBLIC_KB_EDIT)
    public Result<Void> deleteDocument(@PathVariable String documentId) {
        service.deleteDocument(documentId);
        return Result.success();
    }

    @GetMapping("/releases")
    @RequireAdminPermission(AdminPermission.PUBLIC_KB_READ)
    public Result<List<UserAgentKnowledgeRelease>> releases() {
        return Result.success(service.list());
    }

    @GetMapping("/releases/{releaseId}")
    @RequireAdminPermission(AdminPermission.PUBLIC_KB_READ)
    public Result<Map<String, Object>> release(@PathVariable String releaseId) {
        return Result.success(service.releaseDetail(releaseId));
    }

    @PostMapping("/releases")
    @RequireAdminPermission(AdminPermission.PUBLIC_KB_EDIT)
    public Result<UserAgentKnowledgeRelease> createRelease(@RequestBody Map<String, Object> body) {
        return Result.success(service.create(String.valueOf(body.get("kb_id")),
                Integer.parseInt(String.valueOf(body.getOrDefault("release_version", 0)))));
    }

    @PostMapping("/releases/{releaseId}/documents")
    @RequireAdminPermission(AdminPermission.PUBLIC_KB_EDIT)
    public Result<Void> bindDocument(@PathVariable String releaseId,
                                     @RequestBody Map<String, Object> body) {
        service.bindDocument(releaseId, String.valueOf(body.get("document_id")),
                Integer.parseInt(String.valueOf(body.get("document_version"))),
                body.get("category") == null ? "GENERAL" : String.valueOf(body.get("category")));
        return Result.success();
    }

    @DeleteMapping("/releases/{releaseId}/documents/{documentId}/versions/{version}")
    @RequireAdminPermission(AdminPermission.PUBLIC_KB_EDIT)
    public Result<Void> unbindDocument(@PathVariable String releaseId,
                                       @PathVariable String documentId,
                                       @PathVariable Integer version) {
        service.unbindDocument(releaseId, documentId, version);
        return Result.success();
    }

    @PostMapping("/releases/{releaseId}/abandon")
    @RequireAdminPermission(AdminPermission.PUBLIC_KB_EDIT)
    public Result<Void> abandonRelease(@PathVariable String releaseId) {
        service.abandonRelease(releaseId);
        return Result.success();
    }

    @PostMapping("/releases/{releaseId}/review")
    @RequireAdminPermission(AdminPermission.PUBLIC_KB_REVIEW)
    public Result<Void> review(@PathVariable String releaseId, @RequestBody Map<String, Object> body) {
        service.review(releaseId,
                Boolean.parseBoolean(String.valueOf(body.getOrDefault("approved", false))),
                body.get("comment") == null ? null : String.valueOf(body.get("comment")));
        return Result.success();
    }

    @PostMapping("/releases/{releaseId}/publish")
    @RequireAdminPermission(AdminPermission.PUBLIC_KB_PUBLISH)
    public Result<Void> publish(@PathVariable String releaseId) {
        service.publish(releaseId);
        return Result.success();
    }

    @PostMapping("/releases/{releaseId}/offline")
    @RequireAdminPermission(AdminPermission.PUBLIC_KB_PUBLISH)
    public Result<Void> offline(@PathVariable String releaseId) {
        service.offline(releaseId);
        return Result.success();
    }

    @PostMapping("/releases/{releaseId}/rollback")
    @RequireAdminPermission(AdminPermission.PUBLIC_KB_PUBLISH)
    public Result<Void> rollback(@PathVariable String releaseId) {
        service.rollback(releaseId);
        return Result.success();
    }

    @PostMapping("/releases/{releaseId}/bindings")
    @RequireAdminPermission(AdminPermission.PUBLIC_KB_PUBLISH)
    public Result<Void> bind(@PathVariable String releaseId, @RequestBody Map<String, Object> body) {
        service.bind(releaseId,
                body.get("scene") == null ? "USER_CHAT" : String.valueOf(body.get("scene")));
        return Result.success();
    }
}
