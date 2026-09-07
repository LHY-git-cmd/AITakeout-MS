package com.sky.service.agent;

import com.sky.agent.AgentClient;
import com.sky.context.BaseContext;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.dto.KnowledgeBaseDTO;
//import com.sky.dto.KnowledgeBasePageQueryDTO;
import com.sky.result.PageResult;
import com.sky.entity.AgentKnowledgeBase;
import com.sky.entity.AgentKnowledgeDocument;
import com.sky.entity.AgentKnowledgeIndexTask;
import com.sky.exception.AgentBusinessException;
import com.sky.mapper.AgentKnowledgeMapper;
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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 智能代理知识库服务，负责知识库的创建、管理、文档处理和索引。
 * <p>
 * 该服务实现了 {@link ApplicationRunner} 接口，以便在应用启动时恢复未完成的索引任务。
 * 它通过与一个外部的 "agent" 服务（通过 {@link AgentClient}）协作，来处理文档的向量化和索引。
 * 所有文件操作和索引任务都设计为异步执行，以提高系统的响应性和健壮性。
 * </p>
 */
/**
 * 智能代理知识库服务，负责知识库的创建、管理、文档处理和索引。
 * <p>
 * 该服务实现了 {@link ApplicationRunner} 接口，以便在应用启动时恢复未完成的索引任务。
 * 它通过与一个外部的 "agent" 服务（通过 {@link AgentClient}）协作，来处理文档的向量化和索引。
 * 所有文件操作和索引任务都设计为异步执行，以提高系统的响应性和健壮性。
 * </p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AgentKnowledgeService implements ApplicationRunner {

    // 支持的文档类型集合
    // 支持的文档类型集合
    private static final Set<String> TYPES = Set.of("pdf", "docx", "txt", "md", "markdown");

    private final AgentKnowledgeMapper mapper;
    private final AgentClient agentClient;
    private final AgentProperties properties;
    // 用于异步执行索引任务的线程池
    private final ExecutorService indexExecutor = Executors.newFixedThreadPool(2);

    /**
     * 创建一个新的知识库。
     *
     * @param dto 包含知识库名称、描述等信息的DTO。
     * @return 新创建的知识库实体。
     */
    public AgentKnowledgeBase create(KnowledgeBaseDTO dto) {
        Long userId = BaseContext.getCurrentId();
        validateEmbeddingModel(dto.getEmbeddingModel());
        AgentKnowledgeBase value = new AgentKnowledgeBase();
        value.setKbId(UUID.randomUUID().toString());
        value.setName(dto.getName());
        value.setDescription(dto.getDescription());
        value.setEmbeddingModel(defaultValue(dto.getEmbeddingModel(), properties.getEmbeddingModel()));
        value.setChunkStrategy(defaultValue(dto.getChunkStrategy(), "structure"));
        value.setStatus(1);
        value.setCreateUser(userId);
        value.setUpdateUser(userId);
        mapper.insertBase(value);
        return value;
    }

    /**
     * 列出当前用户的所有知识库。
     *
     * @return 知识库列表。
     */
    public List<AgentKnowledgeBase> list() {
        return mapper.listBases(BaseContext.getCurrentId());
    }

//    /**
//     * 分页查询知识库。
//     *
//     * @param pageQueryDTO 分页和查询参数。
//     * @return 分页结果。
//     */
//    public PageResult pageQuery(KnowledgeBasePageQueryDTO pageQueryDTO) {
//        PageHelper.startPage(pageQueryDTO.getPage(), pageQueryDTO.getPageSize());
//        Page<AgentKnowledgeBase> page = mapper.pageQuery(pageQueryDTO);
//        return new PageResult(page.getTotal(), page.getResult());
//    }

    /**
     * 更新指定知识库的信息。
     *
     * @param kbId 知识库ID。
     * @param dto  包含更新信息的DTO。
     */
    /**
     * 更新指定知识库的信息。
     *
     * @param kbId 知识库ID。
     * @param dto  包含更新信息的DTO。
     */
    public void update(String kbId, KnowledgeBaseDTO dto) {
        AgentKnowledgeBase current = requireBase(kbId);
        validateEmbeddingModel(dto.getEmbeddingModel());
        current.setName(dto.getName());
        current.setDescription(dto.getDescription());
        current.setEmbeddingModel(defaultValue(dto.getEmbeddingModel(), current.getEmbeddingModel()));
        current.setChunkStrategy(defaultValue(dto.getChunkStrategy(), current.getChunkStrategy()));
        current.setStatus(dto.getStatus() == null ? current.getStatus() : dto.getStatus());
        current.setUpdateUser(BaseContext.getCurrentId());
        if (mapper.updateBase(current) == 0) {
            throw new AgentBusinessException("知识库更新失败");
        }
    }

    /**
     * 删除一个知识库及其所有相关数据（文档、索引任务、向量）。
     *
     * @param kbId 知识库ID。
     */
    @Transactional
    public void deleteById(String kbId) {
        mapper.deleteById(kbId);
        // TODO: 删除关联的文档和索引
    }

    /**
     * 列出指定知识库中的所有文档。
     *
     * @param kbId 知识库ID。
     * @return 文档列表。
     */
    public List<AgentKnowledgeDocument> listDocuments(String kbId) {
        requireBase(kbId);
        return mapper.listAllDocuments(kbId);
    }

    /**
     * 上传文档并为其创建索引。
     *
     * @param kbId 知识库ID。
     * @param file 上传的文件。
     * @return 创建的文档记录。
     */
    @Transactional
    public AgentKnowledgeDocument upload(String kbId, MultipartFile file) {
        AgentKnowledgeBase kb = requireBase(kbId);
        if (kb.getStatus() != 1) {
            throw new AgentBusinessException("知识库未启用");
        }
        return storeAndIndex(kb, UUID.randomUUID().toString(), 1, null, file);
    }

    /**
     * 为现有文档上传一个新版本，并触发异步索引。
     *
     * @param documentId 文档ID。
     * @param file       上传的新版本文件。
     * @return 创建的新版本文档记录。
     */
    @Transactional
    public AgentKnowledgeDocument uploadVersion(String documentId, MultipartFile file) {
        AgentKnowledgeDocument current = requireDocument(documentId);
        AgentKnowledgeBase kb = requireEnabledBase(current.getKbId());
        requireNoRunningIndex(documentId);
        return storeAndIndex(kb, documentId, mapper.getMaxVersion(documentId) + 1,
                current.getActiveVersion(), file);
    }

    /**
     * 核心私有方法：存储文件、创建文档记录并提交索引任务。
     *
     * @param kb            知识库实体。
     * @param documentId    文档ID。
     * @param version       文档版本。
     * @param activeVersion 当前激活的版本（用于版本切换）。
     * @param file          上传的文件。
     * @return 创建的文档记录。
     */
    private AgentKnowledgeDocument storeAndIndex(AgentKnowledgeBase kb, String documentId,
                                                  int version, Integer activeVersion,
                                                  MultipartFile file) {
        // 1. 验证文件合法性
        byte[] bytes = validate(file);
        try {
            String original = Paths.get(Objects.requireNonNull(file.getOriginalFilename()))
                    .getFileName()
                    .toString();
            String type = extension(original);
            String hash = sha256(bytes);
            // 2. 检查文件是否重复
            if (mapper.findDuplicate(kb.getKbId(), hash) != null) {
                throw new AgentBusinessException("知识库中已存在相同文件");
            }
            // 3. 存储文件到本地文件系统
            Path root = Paths.get(properties.getKnowledgeStoragePath()).toAbsolutePath().normalize();
            Path target = root.resolve(kb.getKbId()).resolve(documentId)
                    .resolve(version + "." + type).normalize();
            if (!target.startsWith(root)) {
                throw new AgentBusinessException("非法文件路径");
            }
            Files.createDirectories(target.getParent());
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }

            // 4. 在数据库中创建文档记录
            AgentKnowledgeDocument document = new AgentKnowledgeDocument();
            document.setDocumentId(documentId);
            document.setKbId(kb.getKbId());
            document.setFileName(original);
            document.setFileType(type);
            document.setFileUrl(target.toString());
            document.setFileHash(hash);
            document.setVersion(version);
            document.setActiveVersion(activeVersion);
            document.setStatus(0); // 初始状态：待处理
            document.setChunkCount(0);
            document.setCreateUser(BaseContext.getCurrentId());
            mapper.insertDocument(document);
            // 5. 提交异步索引任务
            submitIndex(document, kb);
            return document;
        } catch (AgentBusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AgentBusinessException("文档上传失败: " + exception.getMessage());
        }
    }

    /**
     * 对指定的文档版本重新发起索引。
     *
     * @param documentId 文档ID。
     * @return 创建的索引任务。
     */
    public AgentKnowledgeIndexTask reindex(String documentId) {
        AgentKnowledgeDocument document = requireDocument(documentId);
        AgentKnowledgeBase kb = requireBase(document.getKbId());
        requireNoRunningIndex(documentId);
        return submitIndex(document, kb);
    }

    /**
     * 获取特定索引任务的状态。
     *
     * @param taskId 任务ID。
     * @return 索引任务实体。
     */
    public AgentKnowledgeIndexTask getIndexTask(String taskId) {
        AgentKnowledgeIndexTask task = mapper.getIndexTask(taskId);
        if (task == null) {
            throw new AgentBusinessException("索引任务不存在");
        }
        requireDocument(task.getDocumentId());
        return task;
    }

    /**
     * 删除一个文档的所有版本及其关联的向量。
     *
     * @param documentId 文档ID。
     */
    @Transactional
    public void deleteDocument(String documentId) {
        requireDocument(documentId);
        mapper.deleteDocument(documentId);
        afterCommit(() -> deleteVectorsAsync(documentId));
    }

    /**
     * 获取文档的本地文件资源，用于下载。
     *
     * @param documentId 文档ID。
     * @return Spring的 {@link FileSystemResource}。
     */
    public FileSystemResource content(String documentId) {
        AgentKnowledgeDocument document = requireDocument(documentId);
        Path file = Paths.get(document.getFileUrl()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(file)) {
            throw new AgentBusinessException("原文件不存在");
        }
        return new FileSystemResource(file);
    }

    /**
     * 校验并获取一个已启用的知识库。
     *
     * @param kbId 知识库ID。
     * @return 知识库实体。
     * @throws AgentBusinessException 如果知识库不存在或未启用。
     */
    public AgentKnowledgeBase requireEnabledBase(String kbId) {
        AgentKnowledgeBase kb = requireBase(kbId);
        if (kb.getStatus() != 1) {
            throw new AgentBusinessException("知识库不可用");
        }
        return kb;
    }

    /**
     * 校验并获取一个属于当前用户的知识库。
     *
     * @param kbId 知识库ID。
     * @return 知识库实体。
     * @throws AgentBusinessException 如果知识库不存在或用户无权访问。
     */
    private AgentKnowledgeBase requireBase(String kbId) {
        AgentKnowledgeBase value = mapper.getOwnedBase(kbId, BaseContext.getCurrentId());
        if (value == null) {
            throw new AgentBusinessException("知识库不存在或无权访问");
        }
        return value;
    }

    /**
     * 校验并获取一个属于当前用户的文档。
     *
     * @param id 文档ID。
     * @return 文档实体。
     * @throws AgentBusinessException 如果文档不存在或用户无权访问。
     */
    private AgentKnowledgeDocument requireDocument(String id) {
        AgentKnowledgeDocument value = mapper.getOwnedDocument(id, BaseContext.getCurrentId());
        if (value == null) {
            throw new AgentBusinessException("文档不存在或无权访问");
        }
        return value;
    }

    /**
     * 检查指定文档是否已存在正在运行的索引任务。
     *
     * @param documentId 文档ID。
     * @throws AgentBusinessException 如果存在正在运行的任务。
     */
    private void requireNoRunningIndex(String documentId) {
        if (mapper.countUnfinishedIndexTasks(documentId) > 0) {
            throw new AgentBusinessException("该文档已有索引任务正在处理");
        }
    }

    /**
     * 创建索引任务记录，并在数据库事务提交后，将其提交到线程池执行。
     *
     * @param document 文档实体。
     * @param kb       知识库实体。
     * @return 创建的索引任务。
     */
    private AgentKnowledgeIndexTask submitIndex(AgentKnowledgeDocument document, AgentKnowledgeBase kb) {
        String taskId = UUID.randomUUID().toString();
        // 使用文档ID、版本和文件哈希生成一个指纹，用于幂等性检查
        String fingerprint = sha256((document.getDocumentId()
                + "\0" + document.getVersion()
                + "\0" + document.getFileHash()).getBytes());
        AgentKnowledgeIndexTask task = new AgentKnowledgeIndexTask();
        task.setTaskId(taskId);
        task.setDocumentId(document.getDocumentId());
        task.setDocumentVersion(document.getVersion());
        task.setStatus(0); // 状态：待处理
        task.setProgress(0);
        task.setRequestHash(fingerprint);
        mapper.insertIndexTask(task);
        // 关键：确保在事务提交后才执行异步任务，避免因事务回滚导致数据不一致
        afterCommit(() -> indexExecutor.submit(() -> runIndex(task, document, kb)));
        return task;
    }

    /**
     * 异步执行索引的核心逻辑。
     * 此方法在 {@code indexExecutor} 线程池中运行。
     *
     * @param task     索引任务。
     * @param document 文档实体。
     * @param kb       知识库实体。
     */
    private void runIndex(AgentKnowledgeIndexTask task,
                          AgentKnowledgeDocument document,
                          AgentKnowledgeBase kb) {
        try {
            // 1. 更新任务状态为“处理中”
            task.setStatus(1);
            task.setProgress(5);
            task.setStartedAt(LocalDateTime.now());
            mapper.updateIndexTask(task);
            // 2. 调用外部 agent 服务发起索引请求
            agentClient.indexKnowledge(Map.of(
                    "task_id", task.getTaskId(),
                    "kb_id", document.getKbId(),
                    "document_id", document.getDocumentId(),
                    "document_version", document.getVersion(),
                    "file_name", document.getFileName(),
                    "file_type", document.getFileType(),
                    "embedding_model", kb.getEmbeddingModel(),
                    "request_hash", task.getRequestHash()),
                    new FileSystemResource(document.getFileUrl()));
            // 3. 开始轮询索引结果
            pollIndex(task, document);
        } catch (Exception exception) {
            // 4. 如果发起请求失败，则直接标记任务失败
            failIndex(task, document, exception.getMessage());
        }
    }

    /**
     * 轮询外部 agent 服务以获取索引任务的最终状态。
     *
     * @param task     索引任务。
     * @param document 文档实体。
     * @throws InterruptedException 如果线程被中断。
     */
    private void pollIndex(AgentKnowledgeIndexTask task,
                           AgentKnowledgeDocument document) throws InterruptedException {
        // 轮询最多300次（约5分钟）
        for (int attempt = 0; attempt < 300; attempt++) {
            Map state = agentClient.getKnowledgeIndexStatus(task.getTaskId());
            String status = String.valueOf(state.get("status"));
            task.setProgress(((Number) state.getOrDefault("progress", 0)).intValue());

            if ("completed".equals(status)) {
                // 索引成功
                task.setStatus(3);
                task.setProgress(100);
                task.setFinishedAt(LocalDateTime.now());
                mapper.updateIndexTask(task);

                int chunkCount = ((Number) state.getOrDefault("chunk_count", 0)).intValue();
                if (chunkCount <= 0) {
                    failIndex(task, document, "索引未生成有效分块");
                    return;
                }
                // 激活当前文档版本
                Integer previousVersion = document.getActiveVersion();
                mapper.activateDocumentVersion(document.getDocumentId(), document.getVersion(), chunkCount);
                // 如果存在旧的激活版本，则异步删除旧版本的向量
                if (previousVersion != null && !previousVersion.equals(document.getVersion())) {
                    indexExecutor.submit(() ->
                            deleteVersionVectors(document.getDocumentId(), previousVersion));
                }
                return; // 成功，退出轮询
            }
            if ("failed".equals(status)) {
                // 索引失败
                failIndex(task, document, String.valueOf(state.get("error_msg")));
                return; // 失败，退出轮询
            }
            // 更新任务状态（1: running, 2: embedding）并继续轮询
            task.setStatus("embedding".equals(status) ? 2 : 1);
            mapper.updateIndexTask(task);
            Thread.sleep(1000); // 等待1秒
        }
        // 超时处理
        failIndex(task, document, "索引任务超时");
    }

    /**
     * 将索引任务和文档状态标记为失败。
     *
     * @param task   索引任务。
     * @param document 文档实体。
     * @param error  错误信息。
     */
    private void failIndex(AgentKnowledgeIndexTask task, AgentKnowledgeDocument document, String error) {
        task.setStatus(4); // 状态：失败
        task.setErrorMsg(error);
        task.setFinishedAt(LocalDateTime.now());
        mapper.updateIndexTask(task);

        document.setStatus(4); // 状态：失败
        document.setErrorMsg(error);
        mapper.updateDocumentState(document);
    }

    /**
     * 验证上传文件的合法性（大小、类型、内容）。
     *
     * @param file 上传的文件。
     * @return 文件的字节数组。
     * @throws AgentBusinessException 如果验证失败。
     */
    private byte[] validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AgentBusinessException("文件不能为空");
        }
        if (file.getSize() > properties.getKnowledgeMaxFileSize()) {
            throw new AgentBusinessException("文件不能超过20MB");
        }
        String type = extension(Objects.requireNonNull(file.getOriginalFilename()));
        if (!TYPES.contains(type)) {
            throw new AgentBusinessException("仅支持PDF、DOCX、TXT和Markdown");
        }
        try {
            byte[] bytes = file.getBytes();
            byte[] head = Arrays.copyOf(bytes, Math.min(8, bytes.length));
            // 检查特定文件类型的 "magic numbers"
            if ("pdf".equals(type) && !new String(head).startsWith("%PDF-")) {
                throw new AgentBusinessException("PDF文件头不合法");
            }
            if ("docx".equals(type)
                    && !(head.length >= 2 && head[0] == 'P' && head[1] == 'K')) {
                throw new AgentBusinessException("DOCX文件头不合法");
            }
            // 检查文本文件是否包含二进制内容
            if (!Set.of("pdf", "docx").contains(type)
                    && Arrays.stream(toBoxed(head)).anyMatch(value -> value == 0)) {
                throw new AgentBusinessException("文本文件包含非法二进制内容");
            }
            return bytes;
        } catch (IOException exception) {
            throw new AgentBusinessException("无法读取上传文件");
        }
    }

    private void validateEmbeddingModel(String requestedModel) {
        if (requestedModel != null && !requestedModel.isBlank()
                && !properties.getEmbeddingModel().equals(requestedModel)) {
            throw new AgentBusinessException(
                    "当前仅支持Embedding模型: " + properties.getEmbeddingModel());
        }
    }

    /**
     * 应用启动时执行，用于恢复未完成的索引任务。
     *
     * @param args 应用程序参数。
     */
    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isRecoveryEnabled()) {
            return;
        }
        // 查找所有未完成的任务
        mapper.listUnfinishedIndexTasks().forEach(task -> {
            AgentKnowledgeDocument document = mapper.getDocumentVersion(
                    task.getDocumentId(), task.getDocumentVersion());
            // 如果文档不存在或已被逻辑删除，则跳过
            if (document == null || document.getStatus() == 6) {
                return;
            }
            AgentKnowledgeBase kb = mapper.getBase(document.getKbId());
            if (kb != null) {
                // 重新提交索引任务
                indexExecutor.submit(() -> runIndex(task, document, kb));
            }
        });
    }

    /**
     * 异步删除一个文档所有版本的向量。
     *
     * @param documentId 文档ID。
     */
    private void deleteVectorsAsync(String documentId) {
        indexExecutor.submit(() -> deleteVersionVectors(documentId, null));
    }

    /**
     * 调用 agent 服务删除指定文档版本（或所有版本）的向量。
     *
     * @param documentId 文档ID。
     * @param version    版本号，如果为 null，则删除所有版本。
     */
    private void deleteVersionVectors(String documentId, Integer version) {
        try {
            agentClient.deleteKnowledgeDocument(documentId, version);
        } catch (Exception exception) {
            log.warn("删除文档向量失败，可重试, documentId={}, version={}", documentId, version, exception);
        }
    }

    /**
     * 注册一个在当前数据库事务成功提交后执行的动作。
     * 如果当前没有活动的事务，则立即执行。
     *
     * @param action 要执行的动作。
     */
    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    /**
     * 将 byte[] 转换为 Byte[]。
     */
    private Byte[] toBoxed(byte[] values) {
        Byte[] result = new Byte[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = values[index];
        }
        return result;
    }

    /**
     * 从文件名中提取小写的扩展名。
     */
    private String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
    }

    /**
     * 如果值为空，则返回默认值。
     */
    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * 计算数据的 SHA-256 哈希值。
     */
    private String sha256(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    /**
     * 在应用关闭前，优雅地关闭线程池。
     */
    @PreDestroy
    public void shutdown() {
        indexExecutor.shutdownNow();
    }
}