package com.sky.mapper.user;

import com.sky.entity.AgentMessageCitation;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 用户端知识引用只写入公共知识域。 */
@Mapper
public interface UserAgentCitationMapper {
    @Insert("""
            insert ignore into user_agent_message_citation
                (message_id, kb_id, document_id, document_version, chunk_id,
                 file_name, page_no, score, quote, create_time)
            values
                (#{messageId}, #{kbId}, #{documentId}, #{documentVersion}, #{chunkId},
                 #{fileName}, #{pageNo}, #{score}, #{quote}, now())
            """)
    int insertIfAbsent(AgentMessageCitation value);

    @Select("select * from user_agent_message_citation where message_id = #{messageId} order by score desc limit 5")
    List<AgentMessageCitation> listByMessageId(String messageId);
}
