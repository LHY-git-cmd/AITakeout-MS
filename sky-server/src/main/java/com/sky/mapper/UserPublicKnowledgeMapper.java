package com.sky.mapper;

import com.sky.entity.*;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

/** 用户公共知识内容导入与发布控制面 Mapper。 */
@Mapper
public interface UserPublicKnowledgeMapper {
    @Insert("""
            insert into user_agent_knowledge_base
              (kb_id,name,description,embedding_model,chunk_strategy,status,category,lifecycle_status,create_time,update_time)
            values (#{kbId},#{name},#{description},#{embeddingModel},#{chunkStrategy},#{status},#{category},#{lifecycleStatus},now(),now())
            """)
    int insertBase(AgentKnowledgeBase value);

    @Select("select * from user_agent_knowledge_base where status <> 3 order by update_time desc")
    List<AgentKnowledgeBase> listBases();

    @Select("select * from user_agent_knowledge_base where kb_id=#{kbId} and status <> 3")
    AgentKnowledgeBase getBase(String kbId);

    @Update("""
            update user_agent_knowledge_base set name=#{name},description=#{description},
              embedding_model=#{embeddingModel},chunk_strategy=#{chunkStrategy},status=#{status},
              category=#{category},update_time=now() where kb_id=#{kbId} and status <> 3
            """)
    int updateBase(AgentKnowledgeBase value);

    @Insert("""
            insert into user_agent_knowledge_document
              (document_id,kb_id,file_name,file_type,file_url,file_hash,content_hash,version,
               active_version,status,chunk_count,category,lifecycle_status,review_status,create_user,create_time,update_time)
            values (#{documentId},#{kbId},#{fileName},#{fileType},#{fileUrl},#{fileHash},#{contentHash},#{version},
                    #{activeVersion},#{status},#{chunkCount},#{category},#{lifecycleStatus},#{reviewStatus},#{createUser},now(),now())
            """)
    int insertDocument(AgentKnowledgeDocument value);

    @Select("select * from user_agent_knowledge_document where kb_id=#{kbId} and status <> 6 order by document_id,version desc")
    List<AgentKnowledgeDocument> listDocuments(String kbId);

    @Select("select * from user_agent_knowledge_document where kb_id=#{kbId} order by document_id,version desc")
    List<AgentKnowledgeDocument> listAllDocuments(String kbId);

    @Select("select * from user_agent_knowledge_document where document_id=#{documentId} and status <> 6 order by version desc limit 1")
    AgentKnowledgeDocument getDocument(String documentId);

    @Select("select * from user_agent_knowledge_document where document_id=#{documentId} and status <> 6 order by version")
    List<AgentKnowledgeDocument> listDocumentVersions(String documentId);

    @Select("select * from user_agent_knowledge_document where document_id=#{documentId} and version=#{version} and status <> 6")
    AgentKnowledgeDocument getDocumentVersion(@Param("documentId") String documentId, @Param("version") Integer version);

    @Select("select coalesce(max(version),0) from user_agent_knowledge_document where document_id=#{documentId}")
    int getMaxVersion(String documentId);

    @Select("select * from user_agent_knowledge_document where kb_id=#{kbId} and file_hash=#{hash} and status <> 6 limit 1")
    AgentKnowledgeDocument findDuplicate(@Param("kbId") String kbId, @Param("hash") String hash);

    @Update("""
            update user_agent_knowledge_document
            set active_version=#{version},status=case when version=#{version} then 3 else status end,
                chunk_count=case when version=#{version} then #{chunkCount} else chunk_count end,
                error_msg=case when version=#{version} then null else error_msg end,
                review_status=case when version=#{version} then 'APPROVED' else review_status end,
                reviewed_by=case when version=#{version} then null else reviewed_by end,
                reviewed_at=case when version=#{version} then now() else reviewed_at end,
                lifecycle_status=case when version=#{version} then 'READY' else lifecycle_status end,update_time=now()
            where document_id=#{documentId}
            """)
    int activateDocumentVersion(@Param("documentId") String documentId, @Param("version") Integer version,
                                @Param("chunkCount") Integer chunkCount);

    @Update("update user_agent_knowledge_document set status=#{status},error_msg=#{errorMsg},chunk_count=#{chunkCount},update_time=now() where document_id=#{documentId} and version=#{version}")
    int updateDocumentState(AgentKnowledgeDocument value);

    @Select("""
            select count(*) from user_agent_knowledge_release_document rd
            join user_agent_knowledge_release r on r.release_id=rd.release_id
            where rd.document_id=#{documentId} and r.status not in ('DRAFT','ABANDONED')
            """)
    int countImmutableDocumentReleaseReferences(String documentId);

    @Delete("""
            delete from user_agent_knowledge_release_document
            where document_id=#{documentId} and release_id in
              (select release_id from user_agent_knowledge_release where status in ('DRAFT','ABANDONED'))
            """)
    int deleteDraftDocumentReferences(String documentId);

    @Update("update user_agent_knowledge_document set status=6,lifecycle_status='DELETED',update_time=now() where document_id=#{documentId}")
    int deleteDocument(String documentId);

    @Update("update user_agent_knowledge_document set status=6,lifecycle_status='DELETED',update_time=now() where kb_id=#{kbId} and status<>6")
    int deleteDocumentsByBase(String kbId);

    @Insert("""
            insert into user_agent_knowledge_index_task
              (task_id,document_id,document_version,status,progress,request_hash,create_time,update_time)
            values (#{taskId},#{documentId},#{documentVersion},#{status},#{progress},#{requestHash},now(),now())
            """)
    int insertIndexTask(AgentKnowledgeIndexTask value);

    @Select("select * from user_agent_knowledge_index_task where task_id=#{taskId}")
    AgentKnowledgeIndexTask getIndexTask(String taskId);

    @Select("select count(*) from user_agent_knowledge_index_task where document_id=#{documentId} and status in (0,1,2)")
    int countUnfinishedIndexTasks(String documentId);

    @Update("""
            update user_agent_knowledge_index_task
            set status=4,error_msg=#{error},finished_at=now(),update_time=now()
            where document_id=#{documentId} and status in (0,1,2)
            """)
    int failUnfinishedIndexTasks(@Param("documentId") String documentId,
                                 @Param("error") String error);

    @Update("""
            update user_agent_knowledge_index_task set status=#{status},progress=#{progress},error_msg=#{errorMsg},
              started_at=#{startedAt},finished_at=#{finishedAt},update_time=now() where task_id=#{taskId}
            """)
    int updateIndexTask(AgentKnowledgeIndexTask value);

    @Select("select * from user_agent_knowledge_index_task where status in (0,1,2) order by create_time")
    List<AgentKnowledgeIndexTask> listUnfinishedIndexTasks();

    @Select("select * from user_agent_knowledge_release order by created_at desc")
    List<UserAgentKnowledgeRelease> listReleases();

    @Select("select * from user_agent_knowledge_release where release_id=#{releaseId}")
    UserAgentKnowledgeRelease getRelease(String releaseId);

    @Select("select * from user_agent_knowledge_release where kb_id=#{kbId} order by release_version")
    List<UserAgentKnowledgeRelease> listReleasesByBase(String kbId);

    @Select("select coalesce(max(release_version),0) from user_agent_knowledge_release where kb_id=#{kbId}")
    int getMaxReleaseVersion(String kbId);

    @Insert("insert into user_agent_knowledge_release (release_id,kb_id,release_version,status,created_by,created_at,updated_at) values (#{releaseId},#{kbId},#{releaseVersion},'DRAFT',#{createdBy},now(),now())")
    int insertRelease(UserAgentKnowledgeRelease release);

    @Update("update user_agent_knowledge_release set status=#{status},approved_by=#{approvedBy},approved_at=now(),updated_at=now() where release_id=#{releaseId} and status='DRAFT'")
    int approve(@Param("releaseId") String releaseId, @Param("approvedBy") Long approvedBy, @Param("status") String status);

    @Update("""
            update user_agent_knowledge_release set status=#{status},published_by=#{publishedBy},
              published_at=case when #{status}='PUBLISHED' then now() else published_at end,updated_at=now()
            where release_id=#{releaseId} and status in ('APPROVED','PUBLISHED','OFFLINE')
            """)
    int changeStatus(@Param("releaseId") String releaseId, @Param("publishedBy") Long publishedBy, @Param("status") String status);

    @Update("update user_agent_knowledge_release set status='ABANDONED',updated_at=now() where release_id=#{releaseId} and status='DRAFT'")
    int abandonRelease(String releaseId);

    @Update("update user_agent_knowledge_release set status='ABANDONED',updated_at=now() where kb_id=#{kbId} and status in ('DRAFT','APPROVED')")
    int abandonMutableReleasesByBase(String kbId);

    @Delete("""
            delete from user_agent_knowledge_release_document
            where release_id in
              (select release_id from user_agent_knowledge_release where kb_id=#{kbId} and status='ABANDONED')
            """)
    int deleteAbandonedReleaseDocumentsByBase(String kbId);

    @Insert("insert into user_agent_knowledge_release_document (release_id,document_id,document_version,category,created_at) values (#{releaseId},#{documentId},#{documentVersion},#{category},now())")
    int bindDocument(@Param("releaseId") String releaseId, @Param("documentId") String documentId,
                     @Param("documentVersion") Integer documentVersion, @Param("category") String category);

    @Delete("delete from user_agent_knowledge_release_document where release_id=#{releaseId} and document_id=#{documentId} and document_version=#{documentVersion}")
    int unbindDocument(@Param("releaseId") String releaseId, @Param("documentId") String documentId,
                       @Param("documentVersion") Integer documentVersion);

    @Delete("delete from user_agent_knowledge_release_document where release_id=#{releaseId}")
    int deleteReleaseDocuments(String releaseId);

    @Select("select document_id as documentId,document_version as documentVersion,category from user_agent_knowledge_release_document where release_id=#{releaseId} order by created_at")
    List<Map<String, Object>> listReleaseDocuments(String releaseId);

    @Select("""
            select count(*) from user_agent_knowledge_release_document rd
            left join user_agent_knowledge_document d on d.document_id=rd.document_id and d.version=rd.document_version
            where rd.release_id=#{releaseId} and (d.id is null or d.status<>3)
            """)
    int countInvalidReleaseDocuments(String releaseId);

    @Select("select count(*) from user_agent_knowledge_release_document where release_id=#{releaseId}")
    int countReleaseDocuments(String releaseId);

    @Insert("insert into user_agent_knowledge_binding (binding_id,scene,release_id,status,created_by,created_at) values (#{bindingId},#{scene},#{releaseId},'ACTIVE',#{createdBy},now())")
    int bindScene(UserAgentKnowledgeBinding binding);

    @Update("update user_agent_knowledge_binding set status='INACTIVE' where scene=#{scene} and status='ACTIVE'")
    int disableSceneBindings(String scene);

    @Select("select scene from user_agent_knowledge_binding where release_id=#{releaseId}")
    List<String> listBindingScenes(String releaseId);

    @Select("select count(*) from user_agent_knowledge_binding where scene=#{scene} and release_id=#{releaseId}")
    int countBinding(@Param("scene") String scene, @Param("releaseId") String releaseId);

    @Update("update user_agent_knowledge_binding set status='ACTIVE' where scene=#{scene} and release_id=#{releaseId}")
    int activateBinding(@Param("scene") String scene, @Param("releaseId") String releaseId);

    @Update("update user_agent_knowledge_binding set status='INACTIVE' where release_id=#{releaseId} and status='ACTIVE'")
    int disableBindings(String releaseId);

    @Select("""
            select count(*) from user_agent_knowledge_binding b
            join user_agent_knowledge_release r on r.release_id=b.release_id
            where r.kb_id=#{kbId} and b.status='ACTIVE'
            """)
    int countActiveBindingsByBase(String kbId);

    @Update("""
            update user_agent_knowledge_base set status=2,lifecycle_status='ARCHIVED',update_time=now()
            where kb_id=#{kbId} and status<>3
            """)
    int archiveBase(String kbId);

    @Update("""
            update user_agent_knowledge_base set status=2,lifecycle_status='PURGE_PENDING',update_time=now()
            where kb_id=#{kbId} and lifecycle_status in ('ARCHIVED','PURGE_FAILED')
            """)
    int startPurgeBase(String kbId);

    @Update("""
            update user_agent_knowledge_base set status=3,lifecycle_status='PURGED',update_time=now()
            where kb_id=#{kbId} and lifecycle_status='PURGE_PENDING'
            """)
    int finishPurgeBase(String kbId);

    @Update("""
            update user_agent_knowledge_base set status=2,lifecycle_status='PURGE_FAILED',update_time=now()
            where kb_id=#{kbId} and lifecycle_status='PURGE_PENDING'
            """)
    int failPurgeBase(String kbId);

    @Select("select * from user_agent_knowledge_base where status<>3 and lifecycle_status in ('PURGE_PENDING','PURGE_FAILED')")
    List<AgentKnowledgeBase> listPendingPurgeBases();

    @Select("""
            select count(*) from user_agent_knowledge_index_task t
            join user_agent_knowledge_document d on d.document_id=t.document_id and d.version=t.document_version
            where d.kb_id=#{kbId} and t.status in (0,1,2)
            """)
    int countUnfinishedIndexTasksByBase(String kbId);

    @Update("update user_agent_knowledge_binding set status='ACTIVE' where release_id=#{releaseId} and status='INACTIVE'")
    int activateBindings(String releaseId);

    @Insert("insert into user_agent_knowledge_review (release_id,reviewer_id,decision,comment,created_at) values (#{releaseId},#{reviewerId},#{decision},#{comment},now())")
    int insertReview(UserAgentKnowledgeReview review);

    @Select("select * from user_agent_knowledge_review where release_id=#{releaseId} order by created_at desc")
    List<UserAgentKnowledgeReview> listReviews(String releaseId);

    @Insert("insert into user_agent_knowledge_publish_audit (release_id,action,operator_id,from_status,to_status,detail_json,created_at) values (#{releaseId},#{action},#{operatorId},#{fromStatus},#{toStatus},#{detailJson},now())")
    int insertAudit(UserAgentKnowledgePublishAudit audit);
}
