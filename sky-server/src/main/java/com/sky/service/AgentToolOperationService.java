package com.sky.service;

import com.sky.dto.AgentToolOperationRequest;
import com.sky.vo.AgentToolOperationResponse;

import java.util.Map;

/** Python AI工具调用Java原子业务能力的统一入口。 */
public interface AgentToolOperationService {
    AgentToolOperationResponse execute(AgentToolOperationRequest request);
    AgentToolOperationResponse prepare(AgentToolOperationRequest request);
    Map<String, Object> confirmationStatus(String confirmationId);
    Map<String, Object> decideConfirmation(String confirmationId, Long employeeId, boolean approved);
    AgentToolOperationResponse executeConfirmed(String confirmationId);
}
