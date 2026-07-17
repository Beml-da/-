package com.example.demo.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

/**
 * Spring AI 客服核心服务。
 *
 * 依赖由 Spring Boot Auto-Configuration 自动注入：
 *  - ChatClient (SpringAIConfig 中创建)
 *  - ChatMemory  (Redis ChatMemory Repository 自动配置)
 *  - VectorStore (Redis Vector Store 自动配置)
 */
@Service
public class CustomerServiceAiService {

    private static final Logger log = LoggerFactory.getLogger(CustomerServiceAiService.class);

    /** RAG 检索返回的最大文档数 */
    private static final int TOP_K = 4;

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final ChatMemory chatMemory;

    private static final String SYSTEM_PROMPT = """
            你是「交易行」的 AI 智能客服天天，专门帮在校大学生解答平台相关问题。

            【你的身份】
            - 你是平台官方客服，态度友好、专业、耐心
            - 称呼用户"同学"，口语化交流
            - 每次回答控制在 150 字以内

            【你擅长回答】
            1. 平台使用：如何发布商品、搜索商品、下单、支付、联系卖家
            2. 交易规则：议价规则、发货时效、退款流程、面交注意事项
            3. 信用分体系：如何涨分、被扣分怎么办、各等级权益
            4. 违规处罚：为什么被下架/禁言、如何申诉、处罚标准
            5. 账户安全：密码找回、异地登录、账号注销
            6. 特殊业务：毕业季专区、考研专区、拼车、失物招领规则

            【你不能回答】
            - 超出平台范围的问题
            - 平台不存在的功能（禁止编造）
            - 具体订单状态查询
            - 涉及人身安全、违法行为的问题

            【你不知道时】
            请回复："同学，关于这个问题我了解得不够准确，我帮您转人工客服处理~"

            【重要】如果检索到的规则文档与你的知识冲突，以规则文档为准。
            """;

    @Autowired
    public CustomerServiceAiService(ChatClient chatClient,
                                   VectorStore vectorStore,
                                   ChatMemory chatMemory) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.chatMemory = chatMemory;
    }

    /**
     * 处理用户提问（RAG + 多轮上下文）。
     */
    public CustomerAnswer answer(Long userId, String question) {
        String conversationId = String.valueOf(userId);

        // Step 1: RAG 检索
        String context = retrieveContext(question);

        // Step 2: 构建带 RAG 上下文的 System Prompt
        String enhancedSystem = SYSTEM_PROMPT + "\n\n" +
                "【平台规则（检索结果）】\n" +
                (context.isEmpty() ? "（未检索到相关规则，请基于平台通用知识回答）" : context);

        // Step 3: 调用 ChatClient
        String reply;
        try {
            // 调试：查看 ChatMemory 实际类型 + 调用前记忆条数
            int beforeSize = chatMemory.get(conversationId).size();
            log.info("[CustomerServiceAi] ChatMemory={}, convId={}, beforeSize={}",
                    chatMemory.getClass().getSimpleName(), conversationId, beforeSize);

            reply = chatClient.prompt()
                    .system(enhancedSystem)
                    .user(question)
                    .advisors(a -> a
                            .advisors(
                                    QuestionAnswerAdvisor.builder(vectorStore)
                                            .searchRequest(SearchRequest.builder()
                                                    .query(question)
                                                    .topK(TOP_K)
                                                    .similarityThreshold(0.4)
                                                    .build())
                                            .build(),
                                    MessageChatMemoryAdvisor.builder(chatMemory).build()
                            )
                            .param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call()
                    .content();

            // 调试：查看调用后记忆条数
            int afterSize = chatMemory.get(conversationId).size();
            log.info("[CustomerServiceAi] convId={}, afterSize={}", conversationId, afterSize);
        } catch (Exception e) {
            log.error("[CustomerServiceAi] 调用失败 | userId={} | error={}", userId, e.getMessage(), e);
            reply = "同学，AI 服务遇到了点问题，请稍后重试或联系人工客服~";
        }

        return new CustomerAnswer(reply, context);
    }

    /**
     * 清除用户对话上下文。
     */
    public void clearContext(Long userId) {
        try {
            chatMemory.clear(String.valueOf(userId));
            log.info("[CustomerServiceAi] 清除上下文 | userId={}", userId);
        } catch (Exception e) {
            log.warn("[CustomerServiceAi] clear 失败: {}", e.getMessage());
        }
    }

    private String retrieveContext(String question) {
        try {
            var docs = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(question)
                            .topK(TOP_K)
                            .similarityThreshold(0.4)
                            .build()
            );
            if (docs.isEmpty()) return "";
            return docs.stream()
                    .map(doc -> "■ " + doc.getText().replace("#", ""))
                    .collect(Collectors.joining("\n\n"));
        } catch (Exception e) {
            log.warn("[CustomerServiceAi] RAG 检索异常: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 查看对话记忆（调试用）。
     */
    public java.util.List<java.util.Map<String, Object>> getMemory(Long userId) {
        java.util.List<java.util.Map<String, Object>> list = new java.util.ArrayList<>();
        try {
            var messages = chatMemory.get(String.valueOf(userId));
            for (var msg : messages) {
                java.util.Map<String, Object> m = new java.util.HashMap<>();
                m.put("role", msg.getMessageType().name());
                m.put("content", msg.getText());
                list.add(m);
            }
        } catch (Exception e) {
            log.warn("[CustomerServiceAi] 读取记忆失败: {}", e.getMessage());
        }
        return list;
    }

    public record CustomerAnswer(String answer, String context) {}
}