package com.sky.exception;

/** AI写操作确认凭证已失效、已处理或状态冲突。 */
public class AgentConfirmationConflictException extends AgentBusinessException {
    public AgentConfirmationConflictException(String message) {
        super(message);
    }
}
