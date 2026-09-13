package com.sky.exception;

/** 当前管理员不具备请求操作所需权限。 */
public class PermissionDeniedException extends BaseException {
    public PermissionDeniedException(String message) {
        super(message);
    }
}
