package com.sky.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Agent会话详情VO（包含会话信息+消息历史）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Agent会话详情响应数据（含消息历史）")
public class AgentSessionDetailVO implements Serializable {

    /**
     * 会话信息
     */
    @Schema(description = "会话信息")
    private AgentSessionVO session;

    /**
     * 该会话下的所有消息（按seq_no升序）
     */
    @Schema(description = "该会话下的所有消息（按序号升序）")
    private List<AgentMessageVO> messages;
}