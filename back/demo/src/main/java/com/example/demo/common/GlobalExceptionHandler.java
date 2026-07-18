package com.example.demo.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理：把异常统一转换为 Result 返回前端，避免每个 Controller 写 try-catch。
 *
 * 处理顺序：
 *   1) BizException  -> 业务异常，使用异常中携带的 code
 *   2) 参数校验异常  -> 400 + 字段级 message
 *   3) 404 异常      -> 404
 *   4) 其余 Throwable -> 500，记录 ERROR 日志
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException ex, HttpServletRequest req) {
        log.warn("[BizException] {} {} -> code={} msg={}",
                req.getMethod(), req.getRequestURI(), ex.getCode(), ex.getMessage());
        return Result.error(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        return Result.error(400, msg.isEmpty() ? "参数校验失败" : msg);
    }

    @ExceptionHandler(BindException.class)
    public Result<Void> handleBind(BindException ex) {
        String msg = ex.getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        return Result.error(400, msg.isEmpty() ? "参数绑定失败" : msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraint(ConstraintViolationException ex) {
        return Result.error(400, ex.getMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException ex) {
        return Result.error(400, "缺少必填参数: " + ex.getParameterName());
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Result<Void>> handleNotFound(NoHandlerFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "接口不存在: " + ex.getRequestURL()));
    }

    @ExceptionHandler(Throwable.class)
    public Result<Void> handleAny(Throwable ex, HttpServletRequest req) {
        log.error("[UnhandledException] {} {}", req.getMethod(), req.getRequestURI(), ex);
        return Result.error(500, "服务器内部错误: " + ex.getMessage());
    }

    private String formatFieldError(FieldError fe) {
        return fe.getField() + " " + fe.getDefaultMessage();
    }
}