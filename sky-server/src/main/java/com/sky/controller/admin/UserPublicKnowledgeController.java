package com.sky.controller.admin;

import com.sky.annotation.RequireAdminPermission;
import com.sky.dto.KnowledgeBaseDTO;
import com.sky.entity.AgentKnowledgeBase;
import com.sky.entity.AgentKnowledgeDocument;
import com.sky.entity.AgentKnowledgeIndexTask;
import com.sky.entity.UserAgentKnowledgeRelease;
import com.sky.enumeration.AdminPermission;
import com.sky.result.Result;
import com.sky.service.UserPublicKnowledgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 管理端用户公共知识库接口
 * <p>
 * 提供对公共知识库、文档、发布版本及绑定的全生命周期管理功能。
 * 包括知识库的创建、更新、归档和清除；文档的上传、版本管理、删除和重新索引；
 * 发布版本的创建、绑定/解绑文档、审核、发布、下线和回滚等操作。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/admin/agent/public-knowledge")
@RequiredArgsConstructor
@Tag(name = "公共知识库管理接口")
public class UserPublicKnowledgeController {
    private final UserPublicKnowledgeService service;

    /**
     * 获取所有知识库列表。
     *
     * @return 知识库列表。
     */
    @GetMapping("/bases")
    @Operation(summary = "获取知识库列表")
    @RequireAdminPermission(AdminPermission.PUBLIC_KB_READ)
    public Result<List<AgentKnowledgeBase>> bases() {
        log.info("获取所有知识库列表");
        return Result.success(service.listBases());
    }

    /**
     * 创建一个新的知识库。
     *
     * @param body 包含知识库信息的DTO。
     * @return 创建的知识库实体。
     */
    @PostMapping("/bases")
    @Operation(summary = "创建新知识库")
    @RequireAdminPermission(AdminPermission.PUBLIC_KB_EDIT)
    public Result<AgentKnowledgeBase> createBase(@Valid @RequestBody KnowledgeBaseDTO body) {
        log.info("创建一个新的知识库: {}", body);
        return Result.success(service.createBase(body));
    }

    /**
     * 更新指定知识库的信息。
     *
     * @param kbId 知识库ID。
     * @param body 包含更新信息的DTO。
     * @return 成功响应。
     */
    @PutMapping("/bases/{kbId}")
    @Operation(summary = "更新知识库信息")
    @RequireAdminPermission(AdminPermission.PUBLIC_KB_EDIT)
    public Result<Void> updateBase(
            @Parameter(description = "知识库ID") @PathVariable String kbId,
            @Valid @RequestBody KnowledgeBaseDTO body) {
        log.info("更新知识库: kbId={}, body={}", kbId, body);
        service.updateBase(kbId, body);
        return Result.success();
    }

    /**
     * 归档一个知识库。
     *
     * @param kbId 知识库ID。
     * @return 成功响应。
     */
    @PostMapping("/bases/{kbId}/archive")
    @Operation(summary = "归档知识库")
    @RequireAdminPermission(AdminPermission.PUBLIC_KB_PUBLISH)
    public Result<Void> archiveBase(@Parameter(description = "知识库ID") @PathVariable String kbId) {
        log.info("归档知识库: kbId={}", kbId);
        service.archiveBase(kbId);
        return Result.success();
    }

    /**
     * 清除一个知识库及其所有相关数据。
     *
     * @param kbId 知识库ID。
     * @param body 包含确认名称的请求体。
     * @return 成功响应。
     */
    @PostMapping("/bases/{kbId}/purge")
    @Operation(summary = "清除知识库")
    @RequireAdminPermission(AdminPermission.PUBLIC_KB_PUBLISH)
    public Result<Void> purgeBase(
            @Parameter(description = "知识库ID") @PathVariable String kbId,
            @RequestBody Map<String, Object> body) {
        String confirmationName = String.valueOf(body.getOrDefault("confirmation_name", ""));
        log.warn("清除知识库: kbId={}, confirmation_name={}", kbId, confirmationName);
        service.purgeBase(kbId, confirmationName);
        return Result.success();
    }

    /**
     * 获取指定知识库下的所有文档列表。
     *
     * @param kbId 知识库ID。
     * @return 文档列表。
     */
    @GetMapping("/bases/{kbId}/documents")
    @Operation(summary = "获取知识库下的文档列表")
    @RequireAdminPermission(AdminPermission.PUBLIC_KB_READ)
    public Result<List<AgentKnowledgeDocument>> documents(
            @Parameter(description = "知识库ID") @PathVariable String kbId) {
        log.info("获取知识库下的文档列表: kbId={}", kbId);
        return Result.success(service.listDocuments(kbId));
    }

    /**
     * 上传一个新文档到指定的知识库。
     *
     * @param kbId     知识库ID。
     * @param category 文档分类。
     * @param file     上传的文档文件。
     * @return 创建的文档实体。
     */
    @PostMapping(value = "/bases/{kbId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传新文档")
    @RequireAdminPermission(AdminPermission.PUBLIC_KB_EDIT)
    public Result<AgentKnowledgeDocument> upload(
            @Parameter(description = "知识库ID") @PathVariable String kbId,
            @Parameter(description = "文档分类") @RequestParam(defaultValue = "GENERAL") String category,
            @RequestPart("file") MultipartFile file) {
        log.info("上传新文档: kbId={}, category={}, fileName={}", kbId, category, file.getOriginalFilename());
        return Result.success(service.upload(kbId, category, file));
    }

    /**
     * 为现有文档上传一个新版本。
     *
     * @param documentId 文档ID。
     * @param category   文档分类。
     * @param file       上传的文档文件。
     * @return 更新后的文档实体。
     */
    @PostMapping(value = "/documents/{documentId}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传文档新版本")
    @RequireAdminPermission(AdminPermission.PUBLIC_KB_EDIT)
    public Result<AgentKnowledgeDocument> uploadVersion(
            @Parameter(description = "文档ID") @PathVariable String documentId,
            @Parameter(description = "文档分类") @RequestParam(defaultValue = "GENERAL") String category,
            @RequestPart("file") MultipartFile file) {
        log.info("上传文档新版本: documentId={}, category={}, fileName={}", documentId, category, file.getOriginalFilename());
        return Result.success(service.uploadVersion(documentId, category, file));
    }

    /**
     * 重新索引一个文档。
     *
     * @param documentId 文档ID。
     * @return 索引任务实体。
     */
    @PostMapping("/documents/{documentId}/reindex")
    @Operation(summary = "重新索引文档")
    @RequireAdminPermission(AdminPermission.PUBLIC_KB_EDIT)
    public Result<AgentKnowledgeIndexTask> reindex(
            @Parameter(description = "文档ID") @PathVariable String documentId) {
        log.info("重新索引文档: documentId={}", documentId);
        return Result.success(service.reindex(documentId));
    }

    /**
     * 查询索引任务的状态。
     *
     * @param taskId 任务ID。
     * @return 索引任务实体。
     */
    @GetMapping("/index-tasks/{taskId}")
    @Operation(summary = "查询索引任务状态")
    @RequireAdminPermission(AdminPermission.PUBLIC_KB_READ)
    public Result<AgentKnowledgeIndexTask> indexTask(
            @Parameter(description = "任务ID") @PathVariable String taskId) {
        log.info("查询索引任务状态: taskId={}", taskId);
        return Result.success(service.getIndexTask(taskId));
    }

    /**
     * 删除一个文档。
     *
     * @param documentId 文档ID。
     * @return 成功响应。
     */
    @DeleteMapping("/documents/{documentId}")
    @Operation(summary = "删除文档")
    @RequireAdminPermission(AdminPermission.PUBLIC_KB_EDIT)
    public Result<Void> deleteDocument(
            @Parameter(description = "文档ID") @PathVariable String documentId) {
        log.info("删除文档: documentId={}", documentId);
        service.deleteDocument(documentId);
        return Result.success();
    }

    /**
     * 获取所有发布版本列表。
     *
     * @return 发布版本列表。
     */
    @GetMapping("/releases")
    @Operation(summary = "获取发布版本列表")
    @RequireAdminPermission(AdminPermission.PUBLIC_KB_READ)
    public Result<List<UserAgentKnowledgeRelease>> releases() {
        log.info("获取所有发布版本列表");
        return Result.success(service.list());
    }

    /**
     * 获取指定发布版本的详细信息。
     *
     * @param releaseId 发布版本ID。
     * @return 包含发布版本详情的Map。
     */
    @GetMapping("/releases/{releaseId}")
    @Operation(summary = "获取发布版本详情")
    @RequireAdminPermission(AdminPermission.PUBLIC_KB_READ)
    public Result<Map<String, Object>> release(
            @Parameter(description = "发布版本ID") @PathVariable String releaseId) {
        log.info("获取发布版本详情: releaseId={}", releaseId);
        return Result.success(service.releaseDetail(releaseId));
    }

    /**
     * 创建一个新的发布版本。
     *
     * @param body 包含知识库ID和发布版本的请求体。
     * @return 创建的发布版本实体。
     */
    @PostMapping("/releases")
    @Operation(summary = "创建新发布版本")
    @RequireAdminPermission(AdminPermission.PUBLIC_KB_EDIT)
    public Result<UserAgentKnowledgeRelease> createRelease(@RequestBody Map<String, Object> body) {
        String kbId = String.valueOf(body.get("kb_id"));
        int releaseVersion = Integer.parseInt(String.valueOf(body.getOrDefault("release_version", 0)));
        log.info("创建新发布版本: kbId={}, releaseVersion={}", kbId, releaseVersion);
        return Result.success(service.create(kbId, releaseVersion));
    }

    /**
     * 将文档绑定到指定的发布版本。
     *
     * @param releaseId 发布版本ID。
     * @param body      包含文档ID、版本和分类的请求体。
     * @return 成功响应。
     */
    @PostMapping("/releases/{releaseId}/documents")
    @Operation(summary = "绑定文档到发布版本")
    @RequireAdminPermission(AdminPermission.PUBLIC_KB_EDIT)
    public Result<Void> bindDocument(
            @Parameter(description = "发布版本ID") @PathVariable String releaseId,
            @RequestBody Map<String, Object> body) {
        String documentId = String.valueOf(body.get("document_id"));
        int documentVersion = Integer.parseInt(String.valueOf(body.get("document_version")));
        String category = body.get("category") == null ? "GENERAL" : String.valueOf(body.get("category"));
        log.info("绑定文档到发布版本: releaseId={}, documentId={}, documentVersion={}, category={}",
                releaseId, documentId, documentVersion, category);
        service.bindDocument(releaseId, documentId, documentVersion, category);
        return Result.success();
    }

    /**
     * 从发布版本中解绑指定的文档版本。
     *
     * @param releaseId  发布版本ID。
     * @param documentId 文档ID。
     * @param version    文档版本。
     * @return 成功响应。
     */
    @DeleteMapping("/releases/{releaseId}/documents/{documentId}/versions/{version}")
    @Operation(summary = "从发布版本解绑文档")
    @RequireAdminPermission(AdminPermission.PUBLIC_KB_EDIT)
    public Result<Void> unbindDocument(
            @Parameter(description = "发布版本ID") @PathVariable String releaseId,
            @Parameter(description = "文档ID") @PathVariable String documentId,
            @Parameter(description = "文档版本") @PathVariable Integer version) {
        log.info("从发布版本解绑文档: releaseId={}, documentId={}, version={}", releaseId, documentId, version);
        service.unbindDocument(releaseId, documentId, version);
        return Result.success();
    }

    /**
     * 废弃一个发布版本。
     *
     * @param releaseId 发布版本ID。
     * @return 成功响应。
     */
    @PostMapping("/releases/{releaseId}/abandon")
    @Operation(summary = "废弃发布版本")
    @RequireAdminPermission(AdminPermission.PUBLIC_KB_EDIT)
    public Result<Void> abandonRelease(
            @Parameter(description = "发布版本ID") @PathVariable String releaseId) {
        log.info("废弃发布版本: releaseId={}", releaseId);
        service.abandonRelease(releaseId);
        return Result.success();
    }

    /**
     * 审核一个发布版本。
     *
     * @param releaseId 发布版本ID。
     * @param body      包含审核结果（approved）和评论（comment）的请求体。
     * @return 成功响应。
     */
    @PostMapping("/releases/{releaseId}/review")
    @Operation(summary = "审核发布版本")
    @RequireAdminPermission(AdminPermission.PUBLIC_KB_REVIEW)
    public Result<Void> review(
            @Parameter(description = "发布版本ID") @PathVariable String releaseId,
            @RequestBody Map<String, Object> body) {
        boolean approved = Boolean.parseBoolean(String.valueOf(body.getOrDefault("approved", false)));
        String comment = body.get("comment") == null ? null : String.valueOf(body.get("comment"));
        log.info("审核发布版本: releaseId={}, approved={}, comment={}", releaseId, approved, comment);
        service.review(releaseId, approved, comment);
        return Result.success();
    }

    /**
     * 发布一个已审核通过的发布版本。
     *
     * @param releaseId 发布版本ID。
     * @return 成功响应。
     */
    @PostMapping("/releases/{releaseId}/publish")
    @Operation(summary = "发布版本")
    @RequireAdminPermission(AdminPermission.PUBLIC_KB_PUBLISH)
    public Result<Void> publish(
            @Parameter(description = "发布版本ID") @PathVariable String releaseId) {
        log.info("发布版本: releaseId={}", releaseId);
        service.publish(releaseId);
        return Result.success();
    }

    /**
     * 下线一个已发布的版本。
     *
     * @param releaseId 发布版本ID。
     * @return 成功响应。
     */
    @PostMapping("/releases/{releaseId}/offline")
    @Operation(summary = "下线版本")
    @RequireAdminPermission(AdminPermission.PUBLIC_KB_PUBLISH)
    public Result<Void> offline(
            @Parameter(description = "发布版本ID") @PathVariable String releaseId) {
        log.info("下线版本: releaseId={}", releaseId);
        service.offline(releaseId);
        return Result.success();
    }

    /**
     * 回滚到指定的发布版本。
     *
     * @param releaseId 要回滚到的发布版本ID。
     * @return 成功响应。
     */
    @PostMapping("/releases/{releaseId}/rollback")
    @Operation(summary = "回滚版本")
    @RequireAdminPermission(AdminPermission.PUBLIC_KB_PUBLISH)
    public Result<Void> rollback(
            @Parameter(description = "发布版本ID") @PathVariable String releaseId) {
        log.info("回滚版本: releaseId={}", releaseId);
        service.rollback(releaseId);
        return Result.success();
    }

    /**
     * 将发布版本绑定到特定场景。
     *
     * @param releaseId 发布版本ID。
     * @param body      包含场景信息的请求体。
     * @return 成功响应。
     */
    @PostMapping("/releases/{releaseId}/bindings")
    @Operation(summary = "绑定发布版本到场景")
    @RequireAdminPermission(AdminPermission.PUBLIC_KB_PUBLISH)
    public Result<Void> bind(
            @Parameter(description = "发布版本ID") @PathVariable String releaseId,
            @RequestBody Map<String, Object> body) {
        String scene = body.get("scene") == null ? "USER_CHAT" : String.valueOf(body.get("scene"));
        log.info("绑定发布版本到场景: releaseId={}, scene={}", releaseId, scene);
        service.bind(releaseId, scene);
        return Result.success();
    }
}