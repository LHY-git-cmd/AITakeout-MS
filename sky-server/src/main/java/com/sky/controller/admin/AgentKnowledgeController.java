package com.sky.controller.admin;

import com.sky.annotation.RequireAdminPermission;
import com.sky.dto.KnowledgeBaseDTO;
import com.sky.enumeration.AdminPermission;
import com.sky.entity.AgentKnowledgeBase;
import com.sky.entity.AgentKnowledgeDocument;
import com.sky.entity.AgentKnowledgeIndexTask;
import com.sky.result.Result;
import com.sky.service.agent.AgentKnowledgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Agent知识库管理API
 * <p>
 * 提供对知识库（Knowledge Base）、文档（Document）和索引任务（Index Task）的
 * 全生命周期管理，包括创建、查询、更新、删除等操作。
 * </p>
 */
@RestController
@RequestMapping("/admin/agent")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Agent知识库管理接口")
public class AgentKnowledgeController {

    private final AgentKnowledgeService service;

    /**
     * 创建一个新的知识库。
     *
     * @param dto 包含知识库名称、描述等信息的DTO。
     * @return 新创建的知识库实体。
     */
    @PostMapping("/knowledge-bases")
    @RequireAdminPermission(AdminPermission.KNOWLEDGE_WRITE)
    @Operation(summary = "创建知识库")
    public Result<AgentKnowledgeBase> create(@Valid @RequestBody KnowledgeBaseDTO dto) {
        log.info("创建知识库: {}", dto);
        AgentKnowledgeBase createdBase = service.create(dto);
        return Result.success(createdBase);
    }

    /**
     * 查询所有知识库的列表。
     *
     * @return 知识库实体列表。
     */
    @GetMapping("/knowledge-bases")
    @Operation(summary = "获取知识库列表")
    public Result<List<AgentKnowledgeBase>> list() {
        log.info("获取知识库列表");
        List<AgentKnowledgeBase> list = service.list();
        return Result.success(list);
    }

    /**
     * 更新一个已存在的知识库信息。
     *
     * @param kbId 知识库的唯一标识符。
     * @param dto  包含待更新字段的DTO。
     * @return 成功响应。
     */
    @PutMapping("/knowledge-bases/{kbId}")
    @RequireAdminPermission(AdminPermission.KNOWLEDGE_WRITE)
    @Operation(summary = "更新知识库")
    public Result<Void> update(@PathVariable String kbId, @Valid @RequestBody KnowledgeBaseDTO dto) {
        log.info("更新知识库: kbId={}, data={}", kbId, dto);
        service.update(kbId, dto);
        return Result.success();
    }

    /**
     * 删除一个知识库及其包含的所有文档和索引。
     *
     * @param kbId 要删除的知识库的唯一标识符。
     * @return 成功响应。
     */
    @DeleteMapping("/knowledge-bases/{kbId}")
    @RequireAdminPermission(AdminPermission.KNOWLEDGE_WRITE)
    @Operation(summary = "删除知识库")
    public Result<Void> delete(@PathVariable String kbId) {
        log.info("删除知识库: kbId={}", kbId);
        service.deleteById(kbId);
        return Result.success();
    }

    /**
     * 上传一个新文档到指定的知识库。
     * <p>
     * 上传后，系统会自动触发一个异步的文档索引任务。
     * </p>
     *
     * @param kbId 目标知识库的唯一标识符。
     * @param file 上传的文档文件。
     * @return 新创建的文档实体，包含其唯一ID和初始状态。
     */
    @PostMapping(value = "/knowledge-bases/{kbId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequireAdminPermission(AdminPermission.KNOWLEDGE_WRITE)
    @Operation(summary = "上传文档到知识库")
    public Result<AgentKnowledgeDocument> upload(@PathVariable String kbId, @RequestPart("file") MultipartFile file) {
        log.info("上传文档到知识库: kbId={}, fileName={}", kbId, file.getOriginalFilename());
        AgentKnowledgeDocument document = service.upload(kbId, file);
        return Result.success(document);
    }

    /**
     * 为一个已存在的文档上传新版本。
     * <p>
     * 上传后，旧版本的索引将被替换，系统会触发对新版本的异步索引任务。
     * </p>
     *
     * @param documentId 要更新的文档的唯一标识符。
     * @param file       新版本的文档文件。
     * @return 更新后的文档实体。
     */
    @PostMapping(value = "/documents/{documentId}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequireAdminPermission(AdminPermission.KNOWLEDGE_WRITE)
    @Operation(summary = "上传新版本的文档")
    public Result<AgentKnowledgeDocument> uploadVersion(@PathVariable String documentId,
                                                         @RequestPart("file") MultipartFile file) {
        log.info("上传新版本的文档: documentId={}, fileName={}", documentId, file.getOriginalFilename());
        AgentKnowledgeDocument document = service.uploadVersion(documentId, file);
        return Result.success(document);
    }

    /**
     * 查询指定知识库下的所有文档列表。
     *
     * @param kbId 知识库的唯一标识符。
     * @return 该知识库下的文档实体列表。
     */
    @GetMapping("/knowledge-bases/{kbId}/documents")
    @Operation(summary = "获取知识库中的文档列表")
    public Result<List<AgentKnowledgeDocument>> documents(@PathVariable String kbId) {
        log.info("获取知识库中的文档列表: kbId={}", kbId);
        List<AgentKnowledgeDocument> documents = service.listDocuments(kbId);
        return Result.success(documents);
    }

    /**
     * 手动触发对一个文档的重新索引。
     * <p>
     * 当索引策略或模型变更时，可能需要此操作。
     * </p>
     *
     * @param documentId 要重新索引的文档的唯一标识符。
     * @return 新创建的索引任务实体。
     */
    @PostMapping("/documents/{documentId}/reindex")
    @RequireAdminPermission(AdminPermission.KNOWLEDGE_WRITE)
    @Operation(summary = "对文档重新索引")
    public Result<AgentKnowledgeIndexTask> reindex(@PathVariable String documentId) {
        log.info("对文档重新索引: documentId={}", documentId);
        AgentKnowledgeIndexTask task = service.reindex(documentId);
        return Result.success(task);
    }

    /**
     * 从知识库中删除一个文档。
     *
     * @param documentId 要删除的文档的唯一标识符。
     * @return 成功响应。
     */
    @DeleteMapping("/documents/{documentId}")
    @RequireAdminPermission(AdminPermission.KNOWLEDGE_WRITE)
    @Operation(summary = "删除文档")
    public Result<Void> deleteDocument(@PathVariable String documentId) {
        log.info("删除文档: documentId={}", documentId);
        service.deleteDocument(documentId);
        return Result.success();
    }

    /**
     * 查询一个文档索引任务的当前状态。
     *
     * @param taskId 索引任务的唯一标识符。
     * @return 索引任务的实体，包含其状态、进度和可能的错误信息。
     */
    @GetMapping("/index-tasks/{taskId}")
    @Operation(summary = "获取索引任务状态")
    public Result<AgentKnowledgeIndexTask> task(@PathVariable String taskId) {
        log.info("获取索引任务状态: taskId={}", taskId);
        AgentKnowledgeIndexTask task = service.getIndexTask(taskId);
        return Result.success(task);
    }

    /**
     * 下载指定文档的原始文件内容。
     *
     * @param documentId 文档的唯一标识符。
     * @return 一个包含文件内容的HTTP响应实体，可直接被浏览器下载。
     * @throws IOException 如果文件读取失败。
     */
    @GetMapping("/documents/{documentId}/content")
    @Operation(summary = "下载文档内容")
    public ResponseEntity<FileSystemResource> content(@PathVariable String documentId) throws IOException {
        log.info("下载文档内容: documentId={}", documentId);
        FileSystemResource resource = service.content(documentId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(resource.getFilename(), StandardCharsets.UTF_8).build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentLength(resource.contentLength())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}
