package com.sky.exception;

/** 同一task_id携带了不同请求参数或会话。 */
public class AgentTaskConflictException extends AgentBusinessException {
    public AgentTaskConflictException(String message) {
        super(message);
    }
}
