package com.sky.mapper;

import com.sky.entity.AgentKnowledgeBase;
import com.sky.entity.AgentKnowledgeDocument;
import com.sky.entity.AgentKnowledgeIndexTask;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * Agent知识库相关的MyBatis Mapper接口
 * <p>
 * 定义了对 `agent_knowledge_base`、`agent_knowledge_document` 和 `agent_knowledge_index_task`
 * 这三张核心表的数据库操作方法。
 * </p>
 */
@Mapper
public interface AgentKnowledgeMapper {

    /**
     * 插入一个新的知识库记录。
     *
     * @param value 待插入的知识库实体。
     */
    @Insert("""
            insert into agent_knowledge_base
                (kb_id, name, description, embedding_model, chunk_strategy, status,
                 create_user, update_user, create_time, update_time)
            values
                (#{kbId}, #{name}, #{description}, #{embeddingModel}, #{chunkStrategy}, #{status},
                 #{createUser}, #{updateUser}, now(), now())
            """
    )
    void insertBase(AgentKnowledgeBase value);

    /**
     * 查询指定用户的所有未删除的知识库列表。
     *
     * @param userId 用户ID。
     * @return 知识库实体列表。
     */
    @Select("""
            select *
            from agent_knowledge_base
            where create_user = #{userId} and status <> 3
            order by update_time desc
            """
    )
    List<AgentKnowledgeBase> listBases(Long userId);

    @Select("select * from agent_knowledge_base where status <> 3 order by update_time desc")
    List<AgentKnowledgeBase> listAllBases();

    /**
     * 查询指定用户拥有的某个知识库的详细信息。
     *
     * @param kbId   知识库ID。
     * @param userId 用户ID。
     * @return 知识库实体，如果不存在或不属于该用户则返回null。
     */
    @Select("""
            select *
            from agent_knowledge_base
            where kb_id = #{kbId} and create_user = #{userId} and status <> 3
            """
    )
    AgentKnowledgeBase getOwnedBase(@Param("kbId") String kbId, @Param("userId") Long userId);

    /**
     * 根据ID查询一个未删除的知识库。
     *
     * @param kbId 知识库ID。
     * @return 知识库实体。
     */
    @Select("select * from agent_knowledge_base where kb_id = #{kbId} and status <> 3")
    AgentKnowledgeBase getBase(String kbId);

    /**
     * 更新一个知识库的信息。
     *
     * @param value 包含待更新字段的知识库实体。
     * @return 成功更新的行数。
     */
    @Update("""
            update agent_knowledge_base
            set name = #{name}, description = #{description}, embedding_model = #{embeddingModel},
                chunk_strategy = #{chunkStrategy}, status = #{status}, update_user = #{updateUser},
                update_time = now()
            where kb_id = #{kbId} and create_user = #{createUser} and status <> 3
            """
    )
    int updateBase(AgentKnowledgeBase value);

    /**
     * 逻辑删除一个知识库（将其状态设置为3）。
     *
     * @param kbId   要删除的知识库ID。
     * @param userId 执行操作的用户ID。
     * @return 成功删除的行数。
     */
    @Update("""
            update agent_knowledge_base
            set status = 3, update_user = #{userId}, update_time = now()
            where kb_id = #{kbId} and create_user = #{userId} and status <> 3
            """
    )
    int deleteBase(@Param("kbId") String kbId, @Param("userId") Long userId);

    /**
     * 插入一条新的知识库文档记录。
     *
     * @param value 待插入的文档实体。
     */
    @Insert("""
            insert into agent_knowledge_document
                (document_id, kb_id, file_name, file_type, file_url, file_hash, version,
                 active_version, status, chunk_count, create_user, create_time, update_time)
            values
                (#{documentId}, #{kbId}, #{fileName}, #{fileType}, #{fileUrl}, #{fileHash}, #{version},
                 #{activeVersion}, #{status}, #{chunkCount}, #{createUser}, now(), now())
            """
    )
    void insertDocument(AgentKnowledgeDocument value);

    /**
     * 查询指定知识库下的所有未删除的文档列表。
     *
     * @param kbId 知识库ID。
     * @return 文档实体列表。
     */
    @Select("""
            select *
            from agent_knowledge_document
            where kb_id = #{kbId} and status <> 6
            order by create_time desc
            """
    )
    List<AgentKnowledgeDocument> listDocuments(String kbId);

    /**
     * 查询指定知识库下的所有文档（包括已删除的），主要用于清理。
     *
     * @param kbId 知识库ID。
     * @return 文档实体列表。
     */
    @Select("""
            select *
            from agent_knowledge_document
            where kb_id = #{kbId}
            order by document_id, version
            """
    )
    List<AgentKnowledgeDocument> listAllDocuments(String kbId);

    /**
     * 查询指定知识库下所有已准备就绪（索引完成）的文档。
     *
     * @param kbId 知识库ID。
     * @return 就绪状态的文档实体列表。
     */
    @Select("""
            select *
            from agent_knowledge_document
            where kb_id = #{kbId} and status = 3 and active_version = version
            """
    )
    List<AgentKnowledgeDocument> listReadyDocuments(String kbId);

    /**
     * 查询指定用户拥有的某个文档的最新版本信息。
     *
     * @param documentId 文档ID。
     * @param userId     用户ID。
     * @return 文档实体，如果不存在或不属于该用户则返回null。
     */
    @Select("""
            select d.*
            from agent_knowledge_document d
            join agent_knowledge_base k on k.kb_id = d.kb_id
            where d.document_id = #{documentId} and d.status <> 6
              and k.create_user = #{userId} and k.status <> 3
            order by d.version desc
            limit 1
            """
    )
    AgentKnowledgeDocument getOwnedDocument(@Param("documentId") String documentId, @Param("userId") Long userId);

    /**
     * 查询指定文档的特定版本信息。
     *
     * @param documentId 文档ID。
     * @param version    版本号。
     * @return 特定版本的文档实体。
     */
    @Select("""
            select *
            from agent_knowledge_document
            where document_id = #{documentId} and version = #{version}
            """
    )
    AgentKnowledgeDocument getDocumentVersion(@Param("documentId") String documentId, @Param("version") Integer version);

    /**
     * 获取指定文档的最新版本号。
     *
     * @param documentId 文档ID。
     * @return 最大的版本号，如果不存在则返回0。
     */
    @Select("select coalesce(max(version), 0) from agent_knowledge_document where document_id = #{documentId}")
    int getMaxVersion(String documentId);

    /**
     * 在指定知识库中查找具有相同文件哈希值的重复文档。
     *
     * @param kbId     知识库ID。
     * @param fileHash 文件内容的哈希值。
     * @return 如果找到重复文档，则返回其信息；否则返回null。
     */
    @Select("""
            select *
            from agent_knowledge_document
            where kb_id = #{kbId} and file_hash = #{fileHash} and status <> 6
            limit 1
            """
    )
    AgentKnowledgeDocument findDuplicate(@Param("kbId") String kbId, @Param("fileHash") String fileHash);

    /**
     * 更新文档的索引状态信息。
     *
     * @param value 包含状态、块数量、错误信息等更新字段的文档实体。
     * @return 成功更新的行数。
     */
    @Update("""
            update agent_knowledge_document
            set status = #{status}, chunk_count = #{chunkCount}, error_msg = #{errorMsg},
                active_version = #{activeVersion}, update_time = now()
            where document_id = #{documentId} and version = #{version}
            """
    )
    int updateDocumentState(AgentKnowledgeDocument value);

    /**
     * 激活指定文档的某个版本，使其成为在线生效的版本。
     *
     * @param documentId 文档ID。
     * @param version    要激活的版本号。
     * @param chunkCount 该版本的块数量。
     * @return 成功更新的行数。
     */
    @Update("""
            update agent_knowledge_document
            set active_version = #{version},
                status = case when version = #{version} then 3 else status end,
                chunk_count = case when version = #{version} then #{chunkCount} else chunk_count end,
                error_msg = case when version = #{version} then null else error_msg end,
                update_time = now()
            where document_id = #{documentId}
            """
    )
    int activateDocumentVersion(@Param("documentId") String documentId,
                                @Param("version") Integer version,
                                @Param("chunkCount") Integer chunkCount);

    /**
     * 逻辑删除一个文档（将其状态设置为6）。
     *
     * @param documentId 要删除的文档ID。
     * @return 成功删除的行数。
     */
    @Update("update agent_knowledge_document set status = 6, update_time = now() where document_id = #{documentId}")
    int deleteDocument(String documentId);

    /**
     * 逻辑删除指定知识库下的所有文档。
     *
     * @param kbId 知识库ID。
     * @return 成功删除的行数。
     */
    @Update("update agent_knowledge_document set status = 6, update_time = now() where kb_id = #{kbId}")
    int deleteDocumentsByKb(String kbId);

    /**
     * 插入一条新的文档索引任务记录。
     *
     * @param value 待插入的索引任务实体。
     */
    @Insert("""
            insert into agent_knowledge_index_task
                (task_id, document_id, document_version, status, progress,
                 request_hash, create_time, update_time)
            values
                (#{taskId}, #{documentId}, #{documentVersion}, #{status}, #{progress},
                 #{requestHash}, now(), now())
            """
    )
    void insertIndexTask(AgentKnowledgeIndexTask value);

    /**
     * 根据ID查询一个索引任务。
     *
     * @param taskId 任务ID。
     * @return 索引任务实体。
     */
    @Select("select * from agent_knowledge_index_task where task_id = #{taskId}")
    AgentKnowledgeIndexTask getIndexTask(String taskId);

    /**
     * 统计指定文档当前未完成（排队中、处理中、成功）的索引任务数量。
     *
     * @param documentId 文档ID。
     * @return 未完成的任务数量。
     */
    @Select("""
            select count(*)
            from agent_knowledge_index_task
            where document_id = #{documentId} and status in (0, 1, 2)
            """
    )
    int countUnfinishedIndexTasks(String documentId);

    /**
     * 更新一个索引任务的状态和进度。
     *
     * @param value 包含待更新字段的索引任务实体。
     * @return 成功更新的行数。
     */
    @Update("""
            update agent_knowledge_index_task
            set status = #{status}, progress = #{progress}, error_msg = #{errorMsg},
                started_at = #{startedAt}, finished_at = #{finishedAt}, update_time = now()
            where task_id = #{taskId}
            """
    )
    int updateIndexTask(AgentKnowledgeIndexTask value);

    /**
     * 查询所有未完成的索引任务列表。
     *
     * @return 未完成的索引任务实体列表。
     */
    @Select("""
            select *
            from agent_knowledge_index_task
            where status in (0, 1, 2)
            order by create_time
            """
    )
    List<AgentKnowledgeIndexTask> listUnfinishedIndexTasks();

    @Delete({})
    void deleteById(String kbId);
}
