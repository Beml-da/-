package com.example.demo.service;

/**
 * AI 服务自定义异常。封装错误类型和面向前端的友好提示。
 */
public class AiException extends RuntimeException {

    private final Kind kind;

    public AiException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }

    public enum Kind {
        /** API Key 无效 */
        AUTH_INVALID("AI 服务认证失败，请联系管理员"),
        /** 账户余额不足 */
        INSUFFICIENT_BALANCE("AI 服务余额不足，请联系管理员充值"),
        /** 被模型侧限流 */
        RATE_LIMIT("AI 服务调用过于频繁，请稍后再试"),
        /** 请求参数错误 */
        BAD_REQUEST("AI 请求参数错误"),
        /** 模型侧 5xx 错误 */
        UPSTREAM_ERROR("AI 服务暂时不可用，请稍后重试"),
        /** 网络超时 */
        TIMEOUT("AI 响应超时，请检查网络后重试"),
        /** 网络异常 */
        NETWORK("网络异常，请检查连接后重试"),
        /** JSON 解析失败 */
        BAD_JSON("AI 返回格式异常，请重试"),
        /** 返回为空 */
        EMPTY_RESPONSE("AI 没有生成内容，请重试"),
        /** 其他兜底 */
        UNKNOWN("AI 服务异常，请稍后重试");

        public final String friendlyMessage;

        Kind(String friendlyMessage) {
            this.friendlyMessage = friendlyMessage;
        }
    }
}
