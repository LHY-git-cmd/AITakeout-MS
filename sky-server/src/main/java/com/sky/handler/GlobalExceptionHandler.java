package com.sky.handler;

import com.sky.constant.MessageConstant;
import com.sky.exception.BaseException;
import com.sky.exception.AgentTaskConflictException;
import com.sky.exception.AgentConfirmationConflictException;
import com.sky.exception.PermissionDeniedException;
import com.sky.result.Result;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 全局异常处理器，处理项目中抛出的业务异常
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(PermissionDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<String> permissionDeniedExceptionHandler(PermissionDeniedException ex) {
        log.warn("管理端权限拒绝：{}", ex.getMessage());
        return Result.error(ex.getMessage());
    }

    /**
     * 处理 @Valid 校验失败异常（@RequestBody 上的 @Valid 触发）
     *
     * @param ex 参数校验异常
     * @return 校验失败信息
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<String> validationExceptionHandler(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldError() == null
                ? "请求参数不正确"
                : ex.getBindingResult().getFieldError().getDefaultMessage();
        log.warn("请求参数校验失败：{}", message);
        return Result.error(message);
    }

    /**
     * 处理 @Validated 校验异常（方法参数上的约束注解触发）
     *
     * @param ex 约束违反异常
     * @return 校验失败信息
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<String> constraintViolationExceptionHandler(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(violation -> violation.getMessage())
                .findFirst()
                .orElse("请求参数不正确");
        log.warn("请求参数校验失败：{}", message);
        return Result.error(message);
    }

    /**
     * 捕获业务异常
     *
     * @param ex 业务异常
     * @return 统一错误响应
     */
    @ExceptionHandler
    public Result exceptionHandler(BaseException ex){
        log.error("业务异常：{}", ex.getMessage());
        return Result.error(ex.getMessage());
    }

    @ExceptionHandler(AgentTaskConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Result taskConflictExceptionHandler(AgentTaskConflictException ex) {
        log.warn("Agent任务幂等冲突：{}", ex.getMessage());
        return Result.error(ex.getMessage());
    }

    @ExceptionHandler(AgentConfirmationConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Result confirmationConflictExceptionHandler(AgentConfirmationConflictException ex) {
        log.warn("Agent操作确认冲突：{}", ex.getMessage());
        return Result.error(ex.getMessage());
    }

    /**
     * 处理SQL异常
     * 检测到唯一键冲突时给出友好提示，其余未知异常返回通用错误
     *
     * @param ex 其他未捕获的异常
     * @return 统一错误响应
     */
    @ExceptionHandler
    public Result sqlExceptionHandler(Exception ex){
        //Duplicate entry 'zhangsan' for key 'employee.idx_username'
        log.error("未处理异常", ex);
        String message = ex.getMessage();
        if(message != null && message.contains("Duplicate entry")){
            String[] split = ex.getMessage().split(" ");
            String username = split[2];
            String msg = username + MessageConstant.ALREADY_EXISTS;
            return Result.error(msg);
        }else{
            return Result.error(MessageConstant.UNKNOWN_ERROR);
        }
    }
}
