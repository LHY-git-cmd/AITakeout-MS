package com.sky.mapper;

import com.sky.entity.AgentMessageCitation;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Agent消息引用(Citation)的MyBatis Mapper接口
 * <p>
 * 用于持久化和查询智能体在回答问题时引用的知识库来源。
 * </p>
 */
@Mapper
public interface AgentCitationMapper {

    /**
     * 插入一条新的消息引用记录。
     * <p>
     * 使用 {@code insert ignore} 语法，如果记录已存在（基于唯一键），则忽略本次插入，
     * 避免因重复处理事件而导致数据库出错。
     * </p>
     *
     * @param value 待插入的引用实体对象
     * @return 成功插入的行数（0或1）
     */
    @Insert("""
            insert ignore into agent_message_citation
                (message_id, kb_id, document_id, document_version, chunk_id,
                 file_name, page_no, score, quote, create_time)
            values
                (#{messageId}, #{kbId}, #{documentId}, #{documentVersion}, #{chunkId},
                 #{fileName}, #{pageNo}, #{score}, #{quote}, now())
            """)
    int insertIfAbsent(AgentMessageCitation value);

    /**
     * 根据消息ID查询相关的引用列表。
     * <p>
     * 结果按引用分数（score）降序排序，并最多返回5条记录。
     * </p>
     *
     * @param messageId 消息的唯一标识符
     * @return 与该消息关联的引用列表
     */
    @Select("""
            select *
            from agent_message_citation
            where message_id = #{messageId}
            order by score desc
            limit 5
            """)
    List<AgentMessageCitation> listByMessageId(String messageId);
}