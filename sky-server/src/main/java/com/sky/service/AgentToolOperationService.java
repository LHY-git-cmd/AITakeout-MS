package com.sky.service;

import com.sky.dto.AgentToolOperationRequest;
import com.sky.vo.AgentToolOperationResponse;

import java.util.Map;

/** Python AI工具调用Java原子业务能力的统一入口。 */
public interface AgentToolOperationService {
    AgentToolOperationResponse execute(AgentToolOperationRequest request);
    AgentToolOperationResponse prepare(AgentToolOperationRequest request);
    Map<String, Object> confirmationStatus(String confirmationId);
    /** 根据通用主体身份确认或拒绝一次写操作。 */
    Map<String, Object> decideConfirmation(String confirmationId, String actorType,
                                           Long actorId, boolean approved);

    /** 保留管理端现有调用方式。 */
    default Map<String, Object> decideConfirmation(String confirmationId, Long employeeId,
                                                   boolean approved) {
        return decideConfirmation(confirmationId, "ADMIN", employeeId, approved);
    }
    AgentToolOperationResponse executeConfirmed(String confirmationId);
}
