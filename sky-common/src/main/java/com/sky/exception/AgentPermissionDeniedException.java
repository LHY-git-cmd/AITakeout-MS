package com.sky.exception;

/** Agent 私有资源不存在或当前管理员无权访问。 */
public class AgentPermissionDeniedException extends PermissionDeniedException {
    public AgentPermissionDeniedException(String message) {
        super(message);
    }
}
