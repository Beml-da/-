package com.example.demo.config;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Spring AI 全局配置。
 *
 * 修复说明（2026-06-30）：
 *   1. Spring AI 2.0 的 spring-ai-starter-vector-store-redis
 *      在 Jedis 5/6/7 都无法工作：spring-ai-redis-store-2.0.0.jar
 *      内部引用了不存在的 redis.clients.jedis.RedisClient，
 *      RedisVectorStoreAutoConfiguration 类加载即失败，
 *      导致 VectorStore Bean 缺失（应用启动失败）。
 *   2. 改用 Spring AI 内置的 SimpleVectorStore（基于内存 + 持久化文件），
 *      不依赖 Jedis，规则加载后保存到 ./data/vectorstore.json。
 *   3. EmbeddingModel 仍走通义千问 DashScope 兼容接口（DeepSeek 无 embedding）。
 */
@Configuration
public class SpringAIConfig {

    private static final Logger log = LoggerFactory.getLogger(SpringAIConfig.class);

    /**
     * ChatClient 由 auto-config 注入的 ChatModel 创建。
     */
    @Bean
    public ChatClient chatClient(org.springframework.ai.chat.model.ChatModel chatModel) {
        return ChatClient.create(chatModel);
    }

    /**
     * 基于通义千问 DashScope 兼容模式的 EmbeddingModel。
     * 接口：POST https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings
     * 模型：text-embedding-v3（默认 1024 维）
     */
    @Bean
    public EmbeddingModel dashScopeEmbeddingModel(
            @Value("${spring.ai.dashscope.api-key:}") String apiKey,
            @Value("${spring.ai.dashscope.base-url:https://dashscope.aliyuncs.com}") String baseUrl,
            @Value("${spring.ai.dashscope.embedding.options.model:text-embedding-v3}") String model,
            @Value("${spring.ai.dashscope.embedding.options.dimensions:1024}") int dimensions) {

        log.info("[SpringAI] 初始化 DashScope EmbeddingModel | baseUrl={} | model={} | dims={} | keyConfigured={}",
                baseUrl, model, dimensions, !apiKey.isBlank());

        RestClient restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();

        return new EmbeddingModel() {
            @Override
            public EmbeddingResponse call(org.springframework.ai.embedding.EmbeddingRequest request) {
                if (apiKey.isBlank()) {
                    throw new IllegalStateException(
                            "spring.ai.dashscope.api-key 未配置，请在 application.properties 里填入 DashScope API Key");
                }

                List<String> inputs = request.getInstructions();

                JsonMapper jsonMapper = JsonMapper.builder().build();
                ObjectNode root = jsonMapper.createObjectNode();
                root.put("model", model);
                root.put("encoding_format", "float");
                if (model.contains("v3") || model.contains("v4")) {
                    root.put("dimensions", dimensions);
                }
                ArrayNode arr = root.putArray("input");
                for (String s : inputs) {
                    arr.add(s);
                }

                JsonNode resp = restClient.post()
                        .uri("/compatible-mode/v1/embeddings")
                        .body(root.toString())
                        .retrieve()
                        .body(JsonNode.class);

                if (resp == null || !resp.has("data")) {
                    throw new RuntimeException("DashScope embedding 返回为空: " + resp);
                }

                List<Embedding> embeddings = new java.util.ArrayList<>(inputs.size());
                for (JsonNode item : resp.get("data")) {
                    int idx = item.path("index").asInt();
                    ArrayNode vec = (ArrayNode) item.get("embedding");
                    float[] f = new float[vec.size()];
                    for (int i = 0; i < vec.size(); i++) {
                        f[i] = (float) vec.get(i).asDouble();
                    }
                    embeddings.add(new Embedding(f, idx));
                }
                return new EmbeddingResponse(embeddings);
            }

            @Override
            public float[] embed(Document document) {
                String text = document.getFormattedContent(MetadataMode.NONE);
                EmbeddingResponse r = call(
                        new org.springframework.ai.embedding.EmbeddingRequest(
                                List.of(text),
                                org.springframework.ai.embedding.EmbeddingOptions.builder().build()));
                return r.getResult().getOutput();
            }

            @Override
            public int dimensions() {
                return dimensions;
            }
        };
    }

    /**
     * 内存向量库（Spring AI 内置）。
     *
     *   为什么不直接依赖 spring-ai-starter-vector-store-redis 自动装配？
     *   因为 spring-ai-redis-store-2.0.0 内部引用了 Jedis 已经移除的
     *   redis.clients.jedis.RedisClient 类，autoconfig 类加载就失败，
     *   导致整个 VectorStore Bean 都不会被注册。
     *
     *   SimpleVectorStore 完全跑在内存 + 可选的文件持久化（./data/vectorstore.json），
     *   不依赖任何外部服务，启动期不会触发 Embedding 也不依赖 Redis。
     */
    @Bean
    public VectorStore vectorStore(EmbeddingModel dashScopeEmbeddingModel) {
        log.info("[SpringAI] 装配 SimpleVectorStore（内存向量库）");
        return SimpleVectorStore.builder(dashScopeEmbeddingModel).build();
    }
}