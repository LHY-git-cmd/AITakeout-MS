package com.sky.mapper.user;

import com.sky.entity.AgentKnowledgeBase;
import com.sky.entity.AgentKnowledgeDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

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
}
