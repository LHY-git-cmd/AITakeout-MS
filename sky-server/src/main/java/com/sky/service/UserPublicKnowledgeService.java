package com.sky.service;

import com.sky.agent.AgentClient;
import com.sky.agent.AgentClientException;
import com.sky.context.BaseContext;
import com.sky.dto.KnowledgeBaseDTO;
import com.sky.entity.*;
import com.sky.exception.AgentBusinessException;
import com.sky.mapper.UserPublicKnowledgeMapper;
import com.sky.properties.AgentProperties;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** 用户公共知识的内容导入、审核、索引和发布控制面。 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserPublicKnowledgeService implements ApplicationRunner {
    private static final String USER_PROFILE = "USER_ASSISTANT";
    private static final Set<String> TYPES = Set.of("pdf", "docx", "txt", "md", "markdown");

    private final UserPublicKnowledgeMapper mapper;
    private final AgentClient agentClient;
    private final AgentProperties properties;
    private final ExecutorService indexExecutor = Executors.newFixedThreadPool(2);

    public List<AgentKnowledgeBase> listBases() {
        return mapper.listBases();
    }

    public AgentKnowledgeBase createBase(KnowledgeBaseDTO dto) {
        validateEmbeddingModel(dto.getEmbeddingModel());
        AgentKnowledgeBase value = new AgentKnowledgeBase();
        value.setKbId(UUID.randomUUID().toString());
        value.setName(dto.getName());
        value.setDescription(dto.getDescription());
        value.setEmbeddingModel(defaultValue(dto.getEmbeddingModel(), properties.getEmbeddingModel()));
        value.setChunkStrategy(defaultValue(dto.getChunkStrategy(), "structure"));
        value.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        value.setCategory(normalizeCategory(dto.getCategory()));
        value.setLifecycleStatus("DRAFT");
        mapper.insertBase(value);
        return mapper.getBase(value.getKbId());
    }

    public void updateBase(String kbId, KnowledgeBaseDTO dto) {
        AgentKnowledgeBase value = requireBase(kbId);
        requireOperableBase(value);
        validateEmbeddingModel(dto.getEmbeddingModel());
        value.setName(dto.getName());
        value.setDescription(dto.getDescription());
        value.setEmbeddingModel(defaultValue(dto.getEmbeddingModel(), value.getEmbeddingModel()));
        value.setChunkStrategy(defaultValue(dto.getChunkStrategy(), value.getChunkStrategy()));
        value.setStatus(dto.getStatus() == null ? value.getStatus() : dto.getStatus());
        value.setCategory(defaultValue(dto.getCategory(), value.getCategory()));
        if (mapper.updateBase(value) != 1) {
            throw new AgentBusinessException("公共知识库更新失败");
        }
    }

    public List<AgentKnowledgeDocument> listDocuments(String kbId) {
        requireBase(kbId);
        return mapper.listDocuments(kbId);
    }

    @Transactional
    public AgentKnowledgeDocument upload(String kbId, String category, MultipartFile file) {
        AgentKnowledgeBase kb = requireEnabledBase(kbId);
        return storeAndIndex(kb, UUID.randomUUID().toString(), 1, null,
                defaultValue(category, kb.getCategory()), file);
    }

    @Transactional
    public AgentKnowledgeDocument uploadVersion(String documentId, String category, MultipartFile file) {
        AgentKnowledgeDocument current = requireDocument(documentId);
        AgentKnowledgeBase kb = requireEnabledBase(current.getKbId());
        requireNoRunningIndex(documentId);
        return storeAndIndex(kb, documentId, mapper.getMaxVersion(documentId) + 1,
                current.getActiveVersion(), defaultValue(category, current.getCategory()), file);
    }

    @Transactional
    public AgentKnowledgeIndexTask reindex(String documentId) {
        AgentKnowledgeDocument document = requireDocument(documentId);
        releaseStaleIndexTasks(document);
        requireNoRunningIndex(documentId);
        AgentKnowledgeBase base = requireEnabledBase(document.getKbId());
        // 已有可用索引在重建失败时仍可继续服务；重建进度由任务表单独记录。
        if (document.getStatus() == null || document.getStatus() != 3) {
            document.setStatus(1);
            document.setChunkCount(0);
        }
        document.setErrorMsg(null);
        mapper.updateDocumentState(document);
        return submitIndex(document, base);
    }

    public AgentKnowledgeIndexTask getIndexTask(String taskId) {
        AgentKnowledgeIndexTask task = mapper.getIndexTask(taskId);
        if (task == null) {
            throw new AgentBusinessException("索引任务不存在");
        }
        return task;
    }

    @Transactional
    public void deleteDocument(String documentId) {
        AgentKnowledgeDocument document = requireDocument(documentId);
        releaseStaleIndexTasks(document);
        requireNoRunningIndex(documentId);
        if (mapper.countImmutableDocumentReleaseReferences(documentId) > 0) {
            throw new AgentBusinessException("文档已被审核或历史发布版本引用，不能永久删除");
        }
        List<String> files = mapper.listDocumentVersions(documentId).stream()
                .map(AgentKnowledgeDocument::getFileUrl).filter(Objects::nonNull).toList();
        // 草稿可变，删除文档时自动解除所有草稿引用，避免失败文档被草稿锁死。
        mapper.deleteDraftDocumentReferences(documentId);
        if (mapper.deleteDocument(documentId) == 0) {
            throw new AgentBusinessException("公共知识文档删除失败");
        }
        afterCommit(() -> indexExecutor.submit(() -> purgeDocumentAssets(documentId, files)));
    }

    @Transactional
    public void archiveBase(String kbId) {
        AgentKnowledgeBase base = requireBase(kbId);
        if ("ARCHIVED".equals(base.getLifecycleStatus())) return;
        long operator = currentOperator();
        List<UserAgentKnowledgeRelease> releases = mapper.listReleasesByBase(kbId);
        Map<String, List<Map<String, Object>>> published = new LinkedHashMap<>();
        for (UserAgentKnowledgeRelease release : releases) {
            mapper.disableBindings(release.getReleaseId());
            if ("PUBLISHED".equals(release.getStatus())) {
                published.put(release.getReleaseId(), mapper.listReleaseDocuments(release.getReleaseId()));
                if (mapper.changeStatus(release.getReleaseId(), operator, "OFFLINE") != 1) {
                    throw new AgentBusinessException("知识库撤下时发布版本状态已变化");
                }
                audit(release.getReleaseId(), "ARCHIVE_KB", "PUBLISHED", "OFFLINE", operator);
            } else if ("DRAFT".equals(release.getStatus()) || "APPROVED".equals(release.getStatus())) {
                audit(release.getReleaseId(), "ARCHIVE_KB", release.getStatus(), "ABANDONED", operator);
            }
        }
        mapper.abandonMutableReleasesByBase(kbId);
        mapper.deleteAbandonedReleaseDocumentsByBase(kbId);
        if (mapper.archiveBase(kbId) != 1) {
            throw new AgentBusinessException("知识库撤下失败");
        }
        audit("kb:" + kbId, "ARCHIVE_KB", base.getLifecycleStatus(), "ARCHIVED", operator);
        afterCommit(() -> published.forEach((releaseId, documents) ->
                indexExecutor.submit(() -> deactivateReleaseVectors(releaseId, documents))));
    }

    @Transactional
    public void purgeBase(String kbId, String confirmationName) {
        AgentKnowledgeBase base = requireBase(kbId);
        if (!"ARCHIVED".equals(base.getLifecycleStatus())
                && !"PURGE_FAILED".equals(base.getLifecycleStatus())) {
            throw new AgentBusinessException("请先撤下知识库再永久清除");
        }
        if (!Objects.equals(base.getName(), confirmationName)) {
            throw new AgentBusinessException("知识库名称确认不匹配");
        }
        if (mapper.countActiveBindingsByBase(kbId) > 0) {
            throw new AgentBusinessException("知识库仍有活动场景绑定");
        }
        if (mapper.countUnfinishedIndexTasksByBase(kbId) > 0) {
            throw new AgentBusinessException("知识库仍有索引任务正在处理");
        }
        if (mapper.listReleasesByBase(kbId).stream()
                .anyMatch(release -> "PUBLISHED".equals(release.getStatus()))) {
            throw new AgentBusinessException("知识库仍有已发布版本，请重新执行撤下");
        }
        long operator = currentOperator();
        List<AgentKnowledgeDocument> documents = mapper.listAllDocuments(kbId);
        mapper.abandonMutableReleasesByBase(kbId);
        mapper.deleteAbandonedReleaseDocumentsByBase(kbId);
        mapper.deleteDocumentsByBase(kbId);
        if (mapper.startPurgeBase(kbId) != 1) {
            throw new AgentBusinessException("知识库永久清除失败");
        }
        audit("kb:" + kbId, "PURGE_KB", base.getLifecycleStatus(), "PURGE_PENDING", operator);
        afterCommit(() -> scheduleBasePurge(kbId, documents, operator));
    }

    public List<UserAgentKnowledgeRelease> list() {
        return mapper.listReleases();
    }

    public Map<String, Object> releaseDetail(String releaseId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("release", requiredRelease(releaseId));
        result.put("documents", mapper.listReleaseDocuments(releaseId));
        result.put("reviews", mapper.listReviews(releaseId));
        return result;
    }

    @Transactional
    public UserAgentKnowledgeRelease create(String kbId, int version) {
        requireOperableBase(requireBase(kbId));
        long operator = currentOperator();
        UserAgentKnowledgeRelease value = new UserAgentKnowledgeRelease();
        value.setReleaseId("release-public-" + UUID.randomUUID().toString().replace("-", ""));
        value.setKbId(kbId);
        value.setReleaseVersion(version > 0 ? version : mapper.getMaxReleaseVersion(kbId) + 1);
        value.setCreatedBy(operator);
        mapper.insertRelease(value);
        audit(value.getReleaseId(), "CREATE", null, "DRAFT", operator);
        return mapper.getRelease(value.getReleaseId());
    }

    @Transactional
    public void bindDocument(String releaseId, String documentId, int version, String category) {
        UserAgentKnowledgeRelease release = requiredRelease(releaseId);
        requireOperableBase(requireBase(release.getKbId()));
        if (!"DRAFT".equals(release.getStatus())) {
            throw new AgentBusinessException("只有草稿发布版本可以添加文档");
        }
        AgentKnowledgeDocument document = requireDocumentVersion(documentId, version);
        if (!Objects.equals(release.getKbId(), document.getKbId())) {
            throw new AgentBusinessException("文档不属于发布版本的知识库");
        }
        if (document.getStatus() != 3) {
            throw new AgentBusinessException("文档版本必须完成索引");
        }
        mapper.bindDocument(releaseId, documentId, version,
                defaultValue(category, document.getCategory()));
    }

    @Transactional
    public void unbindDocument(String releaseId, String documentId, int version) {
        UserAgentKnowledgeRelease release = requiredRelease(releaseId);
        if (!"DRAFT".equals(release.getStatus())) {
            throw new AgentBusinessException("只有草稿发布版本可以移除文档");
        }
        if (mapper.unbindDocument(releaseId, documentId, version) != 1) {
            throw new AgentBusinessException("草稿中不存在该文档版本");
        }
    }

    @Transactional
    public void abandonRelease(String releaseId) {
        long operator = currentOperator();
        UserAgentKnowledgeRelease release = requiredRelease(releaseId);
        if (!"DRAFT".equals(release.getStatus())) {
            throw new AgentBusinessException("只有草稿发布版本可以废弃");
        }
        mapper.deleteReleaseDocuments(releaseId);
        if (mapper.abandonRelease(releaseId) != 1) {
            throw new AgentBusinessException("发布草稿状态已变化");
        }
        audit(releaseId, "ABANDON", "DRAFT", "ABANDONED", operator);
    }

    @Transactional
    public void review(String releaseId, boolean approved, String comment) {
        long operator = currentOperator();
        UserAgentKnowledgeRelease release = requiredRelease(releaseId);
        requireOperableBase(requireBase(release.getKbId()));
        if (approved && (mapper.countReleaseDocuments(releaseId) == 0
                || mapper.countInvalidReleaseDocuments(releaseId) > 0)) {
            throw new AgentBusinessException("发布版本存在未完成索引的文档");
        }
        String target = approved ? "APPROVED" : "DRAFT";
        if (mapper.approve(releaseId, operator, target) != 1) {
            throw new AgentBusinessException("发布版本状态已变化");
        }
        UserAgentKnowledgeReview review = new UserAgentKnowledgeReview();
        review.setReleaseId(releaseId);
        review.setReviewerId(operator);
        review.setDecision(approved ? "APPROVED" : "REJECTED");
        review.setComment(comment);
        mapper.insertReview(review);
        audit(releaseId, "REVIEW", release.getStatus(), target, operator);
    }

    @Transactional
    public void publish(String releaseId) {
        changeRelease(releaseId, "PUBLISHED", "PUBLISH", true, Set.of("APPROVED"));
    }

    @Transactional
    public void offline(String releaseId) {
        long operator = currentOperator();
        UserAgentKnowledgeRelease release = requiredRelease(releaseId);
        if (!"PUBLISHED".equals(release.getStatus())) {
            throw new AgentBusinessException("发布版本当前状态不允许执行此操作");
        }
        List<Map<String, Object>> documents = mapper.listReleaseDocuments(releaseId);
        // 数据库绑定是用户读取的最终开关；先撤权，远端向量元数据在提交后清理。
        mapper.disableBindings(releaseId);
        if (mapper.changeStatus(releaseId, operator, "OFFLINE") != 1) {
            throw new AgentBusinessException("发布版本状态已变化");
        }
        audit(releaseId, "OFFLINE", "PUBLISHED", "OFFLINE", operator);
        afterCommit(() -> indexExecutor.submit(() -> deactivateReleaseVectors(releaseId, documents)));
    }

    @Transactional
    public void rollback(String releaseId) {
        UserAgentKnowledgeRelease release = requiredRelease(releaseId);
        requireOperableBase(requireBase(release.getKbId()));
        changeRelease(releaseId, "PUBLISHED", "ROLLBACK", true, Set.of("OFFLINE"));
        mapper.listBindingScenes(releaseId).forEach(mapper::disableSceneBindings);
        mapper.activateBindings(releaseId);
    }

    @Transactional
    public void bind(String releaseId, String scene) {
        long operator = currentOperator();
        UserAgentKnowledgeRelease release = requiredRelease(releaseId);
        requireOperableBase(requireBase(release.getKbId()));
        if (!"PUBLISHED".equals(release.getStatus())) {
            throw new AgentBusinessException("只有已发布版本可以绑定场景");
        }
        UserAgentKnowledgeBinding binding = new UserAgentKnowledgeBinding();
        binding.setBindingId(UUID.randomUUID().toString().replace("-", ""));
        binding.setReleaseId(releaseId);
        binding.setScene(defaultValue(scene, "USER_CHAT"));
        binding.setCreatedBy(operator);
        // 一个场景同一时刻只允许一个发布版本生效，防止下线后意外回退到旧版本。
        mapper.disableSceneBindings(binding.getScene());
        if (mapper.countBinding(binding.getScene(), releaseId) > 0) {
            mapper.activateBinding(binding.getScene(), releaseId);
        } else {
            mapper.bindScene(binding);
        }
    }

    private void changeRelease(String releaseId, String target, String action, boolean active,
                               Set<String> allowedStatuses) {
        long operator = currentOperator();
        UserAgentKnowledgeRelease release = requiredRelease(releaseId);
        requireOperableBase(requireBase(release.getKbId()));
        if (!allowedStatuses.contains(release.getStatus())) {
            throw new AgentBusinessException("发布版本当前状态不允许执行此操作");
        }
        List<Map<String, Object>> documents = mapper.listReleaseDocuments(releaseId);
        if (documents.isEmpty() || mapper.countInvalidReleaseDocuments(releaseId) > 0) {
            throw new AgentBusinessException("发布版本没有可用的已完成索引文档");
        }
        // 先更新向量载荷；远端失败时数据库事务不会进入已发布状态。
        List<Map<String, Object>> remoteDocuments = documents.stream()
                .map(item -> Map.<String, Object>of(
                        "document_id", item.get("documentId"),
                        "document_version", item.get("documentVersion")))
                .toList();
        agentClient.updateKnowledgeRelease(releaseId, remoteDocuments, active);
        if (mapper.changeStatus(releaseId, operator, target) != 1) {
            throw new AgentBusinessException("发布版本状态已变化");
        }
        audit(releaseId, action, release.getStatus(), target, operator);
    }

    private AgentKnowledgeDocument storeAndIndex(AgentKnowledgeBase kb, String documentId,
                                                   int version, Integer activeVersion,
                                                   String category, MultipartFile file) {
        byte[] bytes = validate(file);
        try {
            String original = Paths.get(Objects.requireNonNull(file.getOriginalFilename()))
                    .getFileName().toString();
            String type = extension(original);
            String hash = sha256(bytes);
            if (mapper.findDuplicate(kb.getKbId(), hash) != null) {
                throw new AgentBusinessException("公共知识库中已存在相同文件");
            }
            Path root = Paths.get(properties.getKnowledgeStoragePath(), "user-public")
                    .toAbsolutePath().normalize();
            Path target = root.resolve(kb.getKbId()).resolve(documentId)
                    .resolve(version + "." + type).normalize();
            if (!target.startsWith(root)) {
                throw new AgentBusinessException("非法文件路径");
            }
            Files.createDirectories(target.getParent());
            Files.write(target, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            AgentKnowledgeDocument document = new AgentKnowledgeDocument();
            document.setDocumentId(documentId);
            document.setKbId(kb.getKbId());
            document.setFileName(original);
            document.setFileType(type);
            document.setFileUrl(target.toString());
            document.setFileHash(hash);
            document.setContentHash(hash);
            document.setVersion(version);
            document.setActiveVersion(activeVersion);
            document.setStatus(0);
            document.setChunkCount(0);
            document.setCategory(normalizeCategory(category));
            document.setLifecycleStatus("DRAFT");
            // 公共文档取消人工审核；索引成功后即可加入发布版本。
            document.setReviewStatus("APPROVED");
            document.setCreateUser(currentOperator());
            mapper.insertDocument(document);
            submitIndex(document, kb);
            return document;
        } catch (AgentBusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AgentBusinessException("公共知识文档上传失败: " + exception.getMessage());
        }
    }

    private AgentKnowledgeIndexTask submitIndex(AgentKnowledgeDocument document, AgentKnowledgeBase kb) {
        AgentKnowledgeIndexTask task = new AgentKnowledgeIndexTask();
        task.setTaskId(UUID.randomUUID().toString());
        task.setDocumentId(document.getDocumentId());
        task.setDocumentVersion(document.getVersion());
        task.setStatus(0);
        task.setProgress(0);
        task.setRequestHash(sha256((document.getDocumentId() + "\0" + document.getVersion()
                + "\0" + document.getFileHash()).getBytes()));
        mapper.insertIndexTask(task);
        afterCommit(() -> indexExecutor.submit(() -> runIndex(task, document, kb)));
        return task;
    }

    private void runIndex(AgentKnowledgeIndexTask task, AgentKnowledgeDocument document,
                          AgentKnowledgeBase kb) {
        try {
            task.setStatus(1);
            task.setProgress(5);
            task.setStartedAt(LocalDateTime.now());
            mapper.updateIndexTask(task);
            if (document.getStatus() == null || document.getStatus() != 3) {
                document.setStatus(1);
                document.setErrorMsg(null);
                mapper.updateDocumentState(document);
            }
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("task_id", task.getTaskId());
            request.put("kb_id", document.getKbId());
            request.put("document_id", document.getDocumentId());
            request.put("document_version", document.getVersion());
            request.put("file_name", document.getFileName());
            request.put("file_type", document.getFileType());
            request.put("embedding_model", kb.getEmbeddingModel());
            request.put("request_hash", task.getRequestHash());
            request.put("category", document.getCategory());
            agentClient.indexKnowledge(request, new FileSystemResource(document.getFileUrl()), USER_PROFILE);
            pollIndex(task, document);
        } catch (Exception exception) {
            failIndex(task, document, exception.getMessage());
        }
    }

    @SuppressWarnings("rawtypes")
    private void pollIndex(AgentKnowledgeIndexTask task, AgentKnowledgeDocument document)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(
                Math.max(60, properties.getKnowledgeIndexTimeoutSeconds()));
        int attempt = 0;
        int transientFailures = 0;
        while (System.nanoTime() < deadline) {
            Map state;
            try {
                state = agentClient.getKnowledgeIndexStatus(task.getTaskId(), USER_PROFILE);
                transientFailures = 0;
                task.setErrorMsg(null);
            } catch (AgentClientException exception) {
                transientFailures++;
                if (!exception.isRetryable() || transientFailures > 5) throw exception;
                task.setStatus(1);
                task.setErrorMsg("索引状态查询暂时失败，正在自动重试");
                mapper.updateIndexTask(task);
                Thread.sleep(indexPollDelay(attempt));
                attempt++;
                continue;
            }
            String status = String.valueOf(state.get("status")).toLowerCase(Locale.ROOT);
            task.setProgress(((Number) state.getOrDefault("progress", 0)).intValue());
            if ("completed".equals(status)) {
                int chunks = ((Number) state.getOrDefault("chunk_count", 0)).intValue();
                if (chunks <= 0) {
                    failIndex(task, document, "索引未生成有效分块");
                    return;
                }
                task.setStatus(3);
                task.setProgress(100);
                task.setFinishedAt(LocalDateTime.now());
                mapper.updateIndexTask(task);
                mapper.activateDocumentVersion(document.getDocumentId(), document.getVersion(), chunks);
                return;
            }
            if ("failed".equals(status)) {
                failIndex(task, document, String.valueOf(state.get("error_msg")));
                return;
            }
            task.setStatus("embedding".equals(status) ? 2 : 1);
            mapper.updateIndexTask(task);
            if (document.getStatus() != null && document.getStatus() != 3) {
                int documentStatus = "embedding".equals(status) ? 2 : 1;
                if (document.getStatus() != documentStatus) {
                    document.setStatus(documentStatus);
                    mapper.updateDocumentState(document);
                }
            }
            Thread.sleep(indexPollDelay(attempt));
            attempt++;
        }
        timeoutIndex(task, document);
    }

    private void failIndex(AgentKnowledgeIndexTask task, AgentKnowledgeDocument document, String error) {
        String safeError = error == null ? "索引失败" : error.substring(0, Math.min(error.length(), 1000));
        task.setStatus(4);
        task.setErrorMsg(safeError);
        task.setFinishedAt(LocalDateTime.now());
        mapper.updateIndexTask(task);
        if (document.getStatus() != null && document.getStatus() == 3) {
            document.setErrorMsg("最近一次重建失败: " + safeError);
        } else {
            document.setStatus(4);
            document.setChunkCount(0);
            document.setErrorMsg(safeError);
        }
        mapper.updateDocumentState(document);
    }

    private void timeoutIndex(AgentKnowledgeIndexTask task, AgentKnowledgeDocument document) {
        String message = "索引状态确认超时，可稍后重试或重建索引";
        task.setStatus(5);
        task.setErrorMsg(message);
        mapper.updateIndexTask(task);
        if (document.getStatus() != null && document.getStatus() == 3) {
            document.setErrorMsg("最近一次重建超时，原索引仍可用");
        } else {
            document.setStatus(5);
            document.setChunkCount(0);
            document.setErrorMsg(message);
        }
        mapper.updateDocumentState(document);
    }

    private long indexPollDelay(int attempt) {
        if (attempt < 5) return 1000L;
        if (attempt < 15) return 2000L;
        return 5000L;
    }

    private void deactivateReleaseVectors(String releaseId, List<Map<String, Object>> documents) {
        try {
            agentClient.updateKnowledgeRelease(releaseId, toRemoteDocuments(documents), false);
        } catch (Exception exception) {
            log.warn("撤销公共知识发布向量标记失败，数据库访问已停用: releaseId={}", releaseId, exception);
        }
    }

    private List<Map<String, Object>> toRemoteDocuments(List<Map<String, Object>> documents) {
        return documents.stream().map(item -> Map.<String, Object>of(
                "document_id", item.get("documentId"),
                "document_version", item.get("documentVersion"))).toList();
    }

    private boolean purgeDocumentAssets(String documentId, List<String> files) {
        boolean success = true;
        try {
            agentClient.deleteKnowledgeDocument(documentId, null, USER_PROFILE);
        } catch (Exception exception) {
            success = false;
            log.warn("删除用户公共知识向量失败，可重试: documentId={}", documentId, exception);
        }
        for (String file : files) success = deleteStoredFile(file) && success;
        return success;
    }

    private boolean deleteStoredFile(String file) {
        try {
            Path root = Paths.get(properties.getKnowledgeStoragePath(), "user-public")
                    .toAbsolutePath().normalize();
            Path target = Paths.get(file).toAbsolutePath().normalize();
            if (!target.startsWith(root)) {
                log.warn("忽略公共知识存储目录外的文件清理: {}", target);
                return false;
            }
            Files.deleteIfExists(target);
            return true;
        } catch (Exception exception) {
            log.warn("删除公共知识文件失败，可重试: file={}", file, exception);
            return false;
        }
    }

    private void scheduleBasePurge(String kbId, List<AgentKnowledgeDocument> documents, long operator) {
        indexExecutor.submit(() -> {
            Map<String, List<String>> assets = new LinkedHashMap<>();
            for (AgentKnowledgeDocument document : documents) {
                assets.computeIfAbsent(document.getDocumentId(), ignored -> new ArrayList<>());
                if (document.getFileUrl() != null) {
                    assets.get(document.getDocumentId()).add(document.getFileUrl());
                }
            }
            boolean success = true;
            for (Map.Entry<String, List<String>> entry : assets.entrySet()) {
                success = purgeDocumentAssets(entry.getKey(), entry.getValue()) && success;
            }
            if (success) {
                mapper.finishPurgeBase(kbId);
                if (operator > 0) audit("kb:" + kbId, "PURGE_COMPLETE", "PURGE_PENDING", "PURGED", operator);
            } else {
                mapper.failPurgeBase(kbId);
                if (operator > 0) audit("kb:" + kbId, "PURGE_FAILED", "PURGE_PENDING", "PURGE_FAILED", operator);
            }
        });
    }

    private AgentKnowledgeBase requireBase(String kbId) {
        AgentKnowledgeBase value = mapper.getBase(kbId);
        if (value == null) throw new AgentBusinessException("公共知识库不存在");
        return value;
    }

    private AgentKnowledgeBase requireEnabledBase(String kbId) {
        AgentKnowledgeBase value = requireBase(kbId);
        if (value.getStatus() != 1) throw new AgentBusinessException("公共知识库未启用");
        requireOperableBase(value);
        return value;
    }

    private void requireOperableBase(AgentKnowledgeBase value) {
        if ("ARCHIVED".equals(value.getLifecycleStatus())
                || "PURGE_PENDING".equals(value.getLifecycleStatus())
                || "PURGE_FAILED".equals(value.getLifecycleStatus())
                || "PURGED".equals(value.getLifecycleStatus())) {
            throw new AgentBusinessException("知识库已撤下，不能继续编辑或发布");
        }
    }

    private AgentKnowledgeDocument requireDocument(String documentId) {
        AgentKnowledgeDocument value = mapper.getDocument(documentId);
        if (value == null) throw new AgentBusinessException("公共知识文档不存在");
        return value;
    }

    private AgentKnowledgeDocument requireDocumentVersion(String documentId, int version) {
        AgentKnowledgeDocument value = mapper.getDocumentVersion(documentId, version);
        if (value == null) throw new AgentBusinessException("公共知识文档版本不存在");
        return value;
    }

    private UserAgentKnowledgeRelease requiredRelease(String releaseId) {
        UserAgentKnowledgeRelease value = mapper.getRelease(releaseId);
        if (value == null) throw new AgentBusinessException("发布版本不存在");
        return value;
    }

    private void requireNoRunningIndex(String documentId) {
        if (mapper.countUnfinishedIndexTasks(documentId) > 0) {
            throw new AgentBusinessException("该文档已有索引任务正在处理");
        }
    }

    private void releaseStaleIndexTasks(AgentKnowledgeDocument document) {
        if (document.getStatus() != null && (document.getStatus() == 4 || document.getStatus() == 5)) {
            mapper.failUnfinishedIndexTasks(document.getDocumentId(), "文档已失败，旧索引任务已由重试流程终止");
        }
    }

    private byte[] validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new AgentBusinessException("文件不能为空");
        if (file.getSize() > properties.getKnowledgeMaxFileSize()) {
            throw new AgentBusinessException("文件超过允许大小");
        }
        String type = extension(Objects.requireNonNull(file.getOriginalFilename()));
        if (!TYPES.contains(type)) throw new AgentBusinessException("仅支持PDF、DOCX、TXT和Markdown");
        try {
            byte[] bytes = file.getBytes();
            byte[] head = Arrays.copyOf(bytes, Math.min(bytes.length, 8));
            if ("pdf".equals(type) && !new String(head).startsWith("%PDF-")) {
                throw new AgentBusinessException("PDF文件头不合法");
            }
            if ("docx".equals(type) && !(head.length >= 2 && head[0] == 'P' && head[1] == 'K')) {
                throw new AgentBusinessException("DOCX文件头不合法");
            }
            for (byte value : head) {
                if (!Set.of("pdf", "docx").contains(type) && value == 0) {
                    throw new AgentBusinessException("文本文件包含非法二进制内容");
                }
            }
            return bytes;
        } catch (IOException exception) {
            throw new AgentBusinessException("无法读取上传文件");
        }
    }

    private void validateEmbeddingModel(String model) {
        if (model != null && !model.isBlank() && !properties.getEmbeddingModel().equals(model)) {
            throw new AgentBusinessException("当前仅支持Embedding模型: " + properties.getEmbeddingModel());
        }
    }

    private void audit(String releaseId, String action, String from, String to, long operator) {
        UserAgentKnowledgePublishAudit audit = new UserAgentKnowledgePublishAudit();
        audit.setReleaseId(releaseId);
        audit.setAction(action);
        audit.setOperatorId(operator);
        audit.setFromStatus(from);
        audit.setToStatus(to);
        audit.setDetailJson("{}");
        mapper.insertAudit(audit);
    }

    private long currentOperator() {
        Long id = BaseContext.getCurrentId();
        if (id == null) throw new AgentBusinessException("管理员身份不存在");
        return id;
    }

    private String normalizeCategory(String value) {
        return defaultValue(value, "GENERAL").trim().toUpperCase(Locale.ROOT);
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { action.run(); }
        });
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isRecoveryEnabled()) return;
        mapper.listUnfinishedIndexTasks().forEach(task -> {
            AgentKnowledgeDocument document = mapper.getDocumentVersion(
                    task.getDocumentId(), task.getDocumentVersion());
            if (document != null) {
                AgentKnowledgeBase kb = mapper.getBase(document.getKbId());
                if (kb != null) indexExecutor.submit(() -> runIndex(task, document, kb));
            }
        });
        // 应用重启后继续未完成或失败的永久清理任务。
        mapper.listPendingPurgeBases().forEach(base ->
                scheduleBasePurge(base.getKbId(), mapper.listAllDocuments(base.getKbId()), 0L));
    }

    @PreDestroy
    public void shutdown() {
        indexExecutor.shutdownNow();
    }
}
