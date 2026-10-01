package com.sky.mapper.user;

import com.sky.entity.AgentKnowledgeBase;
import com.sky.entity.AgentKnowledgeDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.sky.entity.UserAgentKnowledgeBinding;
import com.sky.entity.UserAgentKnowledgeRelease;

/** 用户端只能读取已经发布的公共知识。 */
@Mapper
public interface UserAgentKnowledgeMapper {
    @Select("select * from user_agent_knowledge_base where kb_id = #{kbId} and status = 1")
    AgentKnowledgeBase getPublishedBase(String kbId);

    @Select("""
            select * from user_agent_knowledge_document
            where kb_id = #{kbId} and status = 3 and active_version = version
            """)
    List<AgentKnowledgeDocument> listPublishedDocuments(String kbId);

    /** 查询当前场景有效的公共知识绑定；用户请求不能传入知识库标识。 */
    @Select("""
            select * from user_agent_knowledge_binding
            where scene = #{scene} and status = 'ACTIVE'
              and (effective_from is null or effective_from <= now())
              and (effective_until is null or effective_until > now())
            order by created_at desc limit 1
            """)
    UserAgentKnowledgeBinding getActiveBinding(@Param("scene") String scene);

    @Select("select * from user_agent_knowledge_release where release_id = #{releaseId}")
    UserAgentKnowledgeRelease getRelease(@Param("releaseId") String releaseId);

    @Select("""
            select d.* from user_agent_knowledge_document d
            join user_agent_knowledge_release_document rd
              on rd.document_id = d.document_id and rd.document_version = d.version
            where rd.release_id = #{releaseId} and d.status = 3
            order by d.document_id
            """)
    List<AgentKnowledgeDocument> listReleaseDocuments(@Param("releaseId") String releaseId);
}
