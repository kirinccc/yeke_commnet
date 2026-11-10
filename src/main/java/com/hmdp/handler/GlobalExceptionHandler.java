package com.hmdp.handler;

import com.hmdp.dto.Result;
import com.hmdp.exception.BaseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLIntegrityConstraintViolationException;

/**
 * 全局异常处理器
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 捕获业务异常，将业务提示返回给前端
     */
    @ExceptionHandler(BaseException.class)
    public Result exceptionHandler(BaseException ex) {
        log.error("业务异常：{}", ex.getMessage());
        return Result.error(ex.getMessage());
    }

    /**
     * 捕获数据库约束异常，避免直接暴露 SQL 信息
     */
    @ExceptionHandler(SQLIntegrityConstraintViolationException.class)
    public Result exceptionHandler(SQLIntegrityConstraintViolationException ex) {
        log.error("数据库约束异常：{}", ex.getMessage());
        String message = ex.getMessage();
        if (message != null && message.contains("Duplicate entry")) {
            return Result.error("数据已存在，请勿重复提交");
        }
        return Result.error("服务器异常");
    }

    /**
     * 捕获其他运行时异常，隐藏服务器内部错误细节
     */
    @ExceptionHandler(RuntimeException.class)
    public Result handleRuntimeException(RuntimeException ex) {
        log.error("服务器异常：", ex);
        return Result.error("服务器异常");
    }
}
