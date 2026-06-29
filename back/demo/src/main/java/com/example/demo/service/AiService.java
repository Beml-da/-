package com.example.demo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 服务：调用大模型 API 为商品自动生成描述文案与价格建议。
 *
 * 能力：
 *  1. Redis 缓存（按 title+category 哈希），相同输入 30 分钟内直接返回，省 API 费用。
 *  2. 异常分类（401/402/429/5xx/超时/JSON 解析失败）→ 抛出 AiException，前端友好提示。
 *  3. Prompt 精细化：固定输出 JSON Schema 与字段约束，确保前端可直接回填。
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    /** Redis 缓存 key 前缀。 */
    private static final String CACHE_KEY_PREFIX = "ai:product:desc:";

    /** 缓存 TTL（30 分钟），防无限堆积 + 偶尔拉新。 */
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    @Value("${ai.api-url}")
    private String apiUrl;

    @Value("${ai.api-key}")
    private String apiKey;

    @Value("${ai.model}")
    private String model;

    @Value("${ai.timeout-seconds:60}")
    private long timeoutSeconds;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RedisTemplate<String, Object> redisTemplate;
    private final RestClient restClient;

    public AiService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.restClient = RestClient.builder()
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * 根据商品名称与分类生成描述文案和价格建议。
     * 命中缓存直接返回；调用失败抛出 {@link AiException}。
     *
     * @return { description, priceSuggestion }
     */
    public Map<String, String> generateProductDescription(String title, String category) {

        String cacheKey = buildCacheKey(title, category);
        Map<String, String> cached = readFromCache(cacheKey);
        if (cached != null) {
            log.info("[AiService] 命中缓存 | key={}", cacheKey);
            return cached;
        }

        try {
            Map<String, String> result = callLlmApi(title, category);
            writeToCache(cacheKey, result);
            return result;
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            log.error("[AiService] 调用失败，未知异常: {}", e.getMessage(), e);
            throw new AiException(AiException.Kind.UNKNOWN, "AI 服务异常，请稍后重试");
        }
    }

    // ================== 核心：调 LLM ==================
    private Map<String, String> callLlmApi(String title, String category)
            throws IOException, AiException {

        String systemPrompt = ""
                + "你是校园二手交易平台「交易行」的资深卖家，擅长撰写真实、亲切、有吸引力的商品描述。\n"
                + "买卖双方都是在校大学生，请用口语化、像学长学姐卖东西的口吻。\n"
                + "\n"
                + "## 输出要求（务必严格遵守）\n"
                + "1. 只输出一个 JSON 对象，不要任何解释、前后缀、markdown 代码块。\n"
                + "2. JSON 字段：\n"
                + "   - description: string，150~300 字，2~4 段。\n"
                + "     内容覆盖：成色说明、使用情况、转手原因、适合人群。\n"
                + "     用 emoji 分隔段落，禁止使用列表/项目符号。\n"
                + "     避免夸大宣传、避免过度营销词汇。\n"
                + "   - priceSuggestion: string，一句话给出价格建议（参考校园二手行情）。\n"
                + "\n"
                + "## 输出示例（仅格式参考，不要照抄）\n"
                + "{\"description\":\"💡这是一段商品描述...\\n\\n📚使用情况：...\",\"priceSuggestion\":\"建议价格：15-25 元\"}";

        String userPrompt = "商品名称：" + title + "\n商品分类：" + (category.isEmpty() ? "未分类" : category);

        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);
        root.put("temperature", 0.7);
        root.put("max_tokens", 800);
        // 强制 JSON 输出（OpenAI 兼容协议）
        root.set("response_format", objectMapper.createObjectNode().put("type", "json_object"));

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

        String responseBody;
        try {
            responseBody = restClient.post()
                    .uri(apiUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .body(root.toString())
                    .retrieve()
                    .body(String.class);
        } catch (HttpClientErrorException e) {
            int status = e.getStatusCode().value();
            String body = e.getResponseBodyAsString();
            log.warn("[AiService] 4xx 异常 | status={} | body={}", status, body);
            AiException.Kind kind = switch (status) {
                case 401 -> AiException.Kind.AUTH_INVALID;       // key 无效
                case 402 -> AiException.Kind.INSUFFICIENT_BALANCE; // 余额不足（部分厂商）
                case 429 -> AiException.Kind.RATE_LIMIT;          // 模型侧限流
                default -> AiException.Kind.BAD_REQUEST;
            };
            throw new AiException(kind, kind.friendlyMessage);
        } catch (HttpServerErrorException e) {
            log.warn("[AiService] 5xx 异常 | status={}", e.getStatusCode().value());
            throw new AiException(AiException.Kind.UPSTREAM_ERROR, AiException.Kind.UPSTREAM_ERROR.friendlyMessage);
        } catch (ResourceAccessException e) {
            // 超时 / 连接失败 / 网络中断
            log.warn("[AiService] 网络异常: {}", e.getMessage());
            throw new AiException(AiException.Kind.TIMEOUT, AiException.Kind.TIMEOUT.friendlyMessage);
        } catch (RestClientException e) {
            log.warn("[AiService] RestClient 异常: {}", e.getMessage());
            throw new AiException(AiException.Kind.NETWORK, AiException.Kind.NETWORK.friendlyMessage);
        }

        JsonNode respRoot = objectMapper.readTree(responseBody);

        // 模型侧业务错误（Http 200 但返回 error 字段）
        if (respRoot.has("error")) {
            String errMsg = respRoot.path("error").path("message").asText("模型返回错误");
            log.warn("[AiService] 模型返回 error: {}", errMsg);
            // 余额不足等通常以 error.code 体现
            String errCode = respRoot.path("error").path("code").asText("");
            AiException.Kind kind = "insufficient_balance".equalsIgnoreCase(errCode)
                    ? AiException.Kind.INSUFFICIENT_BALANCE
                    : AiException.Kind.UPSTREAM_ERROR;
            throw new AiException(kind, kind.friendlyMessage);
        }

        String content = respRoot.path("choices").path(0).path("message").path("content").asText("");
        if (content.isEmpty()) {
            log.warn("[AiService] 模型内容为空 | raw={}", responseBody);
            throw new AiException(AiException.Kind.EMPTY_RESPONSE, "模型本次没有生成内容，请重试");
        }

        // 容错：极少数模型仍可能用 ```json 包裹
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(json)?\\s*", "")
                    .replaceFirst("\\s*```$", "");
        }

        JsonNode result;
        try {
            result = objectMapper.readTree(trimmed);
        } catch (IOException e) {
            log.warn("[AiService] JSON 解析失败 | content={}", content);
            throw new AiException(AiException.Kind.BAD_JSON, "模型返回格式异常，请重试");
        }

        String description = result.path("description").asText("").trim();
        String priceSuggestion = result.path("priceSuggestion").asText("").trim();

        if (description.isEmpty()) {
            log.warn("[AiService] 解析后 description 为空 | content={}", content);
            throw new AiException(AiException.Kind.BAD_JSON, "模型没有给出有效描述，请重试");
        }

        Map<String, String> out = new LinkedHashMap<>();
        out.put("description", description);
        out.put("priceSuggestion", priceSuggestion);
        return out;
    }

    // ================== Redis 缓存 ==================
    @SuppressWarnings("unchecked")
    private Map<String, String> readFromCache(String key) {
        try {
            Object obj = redisTemplate.opsForValue().get(key);
            if (obj instanceof Map) {
                return (Map<String, String>) obj;
            }
        } catch (Exception e) {
            // Redis 挂了不能影响主流程
            log.warn("[AiService] 读缓存失败，继续走远程调用: {}", e.getMessage());
        }
        return null;
    }

    private void writeToCache(String key, Map<String, String> value) {
        try {
            redisTemplate.opsForValue().set(key, value, CACHE_TTL);
        } catch (Exception e) {
            log.warn("[AiService] 写缓存失败，不影响返回: {}", e.getMessage());
        }
    }

    private String buildCacheKey(String title, String category) {
        // SHA-256(title + ":" + category) as hex，避免特殊字符
        try {
            String raw = (title == null ? "" : title.trim()) + ":" + (category == null ? "" : category.trim());
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(CACHE_KEY_PREFIX);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            // 不太可能发生，但 fallback 到原始字符串（虽然不太理想）
            return CACHE_KEY_PREFIX + (title + "_" + category).hashCode();
        }
    }
}
