package com.example.demo.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 基于 RedisTemplate 的 ChatMemory 实现。
 *
 * <p>背景：Spring AI 2.0 的 {@code spring-ai-model-chat-memory-repository-redis}
 * 内部引用了 {@code redis.clients.jedis.RedisClient}，
 * 该类在 Jedis 5.1.0+ 中已被移除，导致 RedisChatMemoryAutoConfiguration
 * 类加载失败，Spring Boot 降级使用内存版 InMemoryChatMemory。
 *
 * <p>本实现使用项目已有的 {@link StringRedisTemplate}，不依赖 Jedis 的
 * {@code RedisClient} 类，与现有的限流、分布式锁等 Redis 功能共用同一连接。
 *
 * <p>存储结构：
 * <pre>{@code
 *   Key:   chat:memory:{conversationId}
 *   Value: Redis List，每个元素是一个 JSON 序列化的消息
 *   TTL:   7 天（可配置）
 * }</pre>
 */
public class RedisChatMemory implements ChatMemory {

    private static final Logger log = LoggerFactory.getLogger(RedisChatMemory.class);

    static final String KEY_PREFIX = "chat:memory:";
    static final Duration DEFAULT_TTL = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public RedisChatMemory(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this(redisTemplate, objectMapper, DEFAULT_TTL);
    }

    public RedisChatMemory(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
    }

    private String key(String conversationId) {
        return KEY_PREFIX + conversationId;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        String k = key(conversationId);
        try {
            List<String> jsonMessages = new ArrayList<>(messages.size());
            for (Message msg : messages) {
                jsonMessages.add(serialize(msg));
            }
            redisTemplate.opsForList().rightPushAll(k, jsonMessages);
            redisTemplate.expire(k, ttl);
            log.debug("[RedisChatMemory] 写入 {} 条消息 | convId={}", messages.size(), conversationId);
        } catch (Exception e) {
            log.error("[RedisChatMemory] 写入失败 | convId={} | error={}", conversationId, e.getMessage());
        }
    }

    @Override
    public List<Message> get(String conversationId) {
        String k = key(conversationId);
        try {
            List<String> jsonList = redisTemplate.opsForList().range(k, 0, -1);
            if (jsonList == null || jsonList.isEmpty()) {
                return Collections.emptyList();
            }
            List<Message> messages = new ArrayList<>(jsonList.size());
            for (String json : jsonList) {
                Message msg = deserialize(json);
                if (msg != null) {
                    messages.add(msg);
                }
            }
            return messages;
        } catch (Exception e) {
            log.error("[RedisChatMemory] 读取失败 | convId={} | error={}", conversationId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public void clear(String conversationId) {
        try {
            redisTemplate.delete(key(conversationId));
            log.debug("[RedisChatMemory] 清除对话 | convId={}", conversationId);
        } catch (Exception e) {
            log.warn("[RedisChatMemory] 清除失败 | convId={} | error={}", conversationId, e.getMessage());
        }
    }

    /**
     * 将 Message 序列化为 JSON 字符串。
     */
    private String serialize(Message msg) throws JsonProcessingException {
        Map<String, Object> dto = Map.of(
                "messageType", msg.getMessageType().getValue(),
                "textContent", msg.getText() != null ? msg.getText() : "",
                "metadata", msg.getMetadata() != null ? msg.getMetadata() : Collections.emptyMap()
        );
        return objectMapper.writeValueAsString(dto);
    }

    /**
     * 将 JSON 字符串反序列化为对应的 Message 子类。
     */
    private Message deserialize(String json) {
        try {
            Map<String, Object> dto = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {});

            String typeStr = (String) dto.getOrDefault("messageType", "USER");
            String text = (String) dto.getOrDefault("textContent", "");
            @SuppressWarnings("unchecked")
            Map<String, Object> meta = (Map<String, Object>) dto.getOrDefault("metadata", Collections.emptyMap());

            MessageType messageType = MessageType.valueOf(typeStr.toUpperCase());

            return switch (messageType) {
                case USER -> UserMessage.builder().text(text).metadata(meta).build();
                case ASSISTANT -> AssistantMessage.builder().content(text).properties(meta).build();
                case SYSTEM -> SystemMessage.builder().text(text).metadata(meta).build();
                case TOOL -> UserMessage.builder().text(text).metadata(meta).build();
            };
        } catch (Exception e) {
            log.warn("[RedisChatMemory] 反序列化失败，跳过: {} | error={}",
                    json.length() > 100 ? json.substring(0, 100) + "..." : json,
                    e.getMessage());
            return null;
        }
    }
}
