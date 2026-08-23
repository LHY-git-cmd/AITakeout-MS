package com.sky.handler;

import com.sky.constant.MessageConstant;
import com.sky.exception.BaseException;
import com.sky.result.Result;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器，处理项目中抛出的业务异常
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

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