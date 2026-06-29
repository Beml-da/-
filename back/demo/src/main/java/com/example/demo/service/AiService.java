package com.example.demo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 服务：调用大模型 API 为商品自动生成描述文案与价格建议。
 *
 * 使用 Spring 6 自带的 RestClient，无需额外 HTTP 客户端依赖。
 * 默认配置 DeepSeek（OpenAI 兼容协议），可平滑切换到智谱、通义千问等。
 */
@Service
public class AiService {

    @Value("${ai.api-url}")
    private String apiUrl;

    @Value("${ai.api-key}")
    private String apiKey;

    @Value("${ai.model}")
    private String model;

    @Value("${ai.timeout-seconds:60}")
    private long timeoutSeconds;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient;

    public AiService() {
        this.restClient = RestClient.builder()
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * 根据商品名称与分类生成描述文案和价格建议。
     *
     * @return { description, priceSuggestion }
     */
    public Map<String, String> generateProductDescription(String title, String category)
            throws RestClientException, IOException {

        String systemPrompt = ""
                + "你是一个校园二手交易平台的资深卖家，擅长撰写真实、亲切、有吸引力的商品描述。\n"
                + "买卖双方都是在校大学生，请用口语化、像学长学姐卖东西的口吻。\n"
                + "请根据用户给出的「商品名称」和「商品分类」输出两段内容：\n"
                + "1. description：商品描述文案，150~300 字，2~4 段。\n"
                + "   应包含：成色说明、使用情况、转手原因、适合人群。\n"
                + "   避免：夸大宣传、过度营销词汇。\n"
                + "   用 emoji 分隔段落，不要用列表。\n"
                + "2. priceSuggestion：一句话给出价格建议（参考校园二手行情）。\n"
                + "严格按 JSON 格式返回，不要任何多余文字，不要 markdown 代码块：\n"
                + "{\"description\":\"...\",\"priceSuggestion\":\"...\"}";

        String userPrompt = "商品名称：" + title + "\n商品分类：" + (category.isEmpty() ? "未分类" : category);

        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);
        root.put("temperature", 0.7);
        root.put("max_tokens", 800);

        ArrayNode messages = objectMapper.createArrayNode();
        ObjectNode sysMsg = objectMapper.createObjectNode();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);
        messages.add(sysMsg);

        ObjectNode userMsg = objectMapper.createObjectNode();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt);
        messages.add(userMsg);

        root.set("messages", messages);

        String responseBody = restClient.post()
                .uri(apiUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .body(root.toString())
                .retrieve()
                .body(String.class);

        JsonNode respRoot = objectMapper.readTree(responseBody);
        String content = respRoot.path("choices").path(0).path("message").path("content").asText("");

        // 容错：去掉 ```json / ``` 包裹
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(json)?\\s*", "")
                    .replaceFirst("\\s*```$", "");
        }

        JsonNode result = objectMapper.readTree(trimmed);
        String description = result.path("description").asText("");
        String priceSuggestion = result.path("priceSuggestion").asText("");

        if (description.isEmpty()) {
            throw new java.io.IOException("模型返回内容为空: " + content);
        }

        Map<String, String> out = new LinkedHashMap<>();
        out.put("description", description);
        out.put("priceSuggestion", priceSuggestion);
        return out;
    }
}
