package com.example.demo.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 应用启动时自动将平台规则文档灌入 SimpleVectorStore（内存向量库）。
 *
 * 工作流程：
 *  1. 读取 classpath:ai/platform-rules.md
 *  2. 按 ## 标题分割成若干章节（保留层级结构）
 *  3. 每个章节截断到 500 字（避免 embedding 超长）
 *  4. SimpleVectorStore 内部用 EmbeddingModel 向量化
 *  5. 持久化到本地文件（./data/vectorstore.json），下次启动可直接复用
 *
 * 注：Spring AI 2.0 的 RedisVectorStore 因上游 bug（Jedis 5/6/7 都缺少
 *     redis.clients.jedis.RedisClient）无法工作，改用 SimpleVectorStore。
 */
@Component
public class RuleDocumentInitializer {

    private static final Logger log = LoggerFactory.getLogger(RuleDocumentInitializer.class);

    private static final String SOURCE_TAG = "platform-rules";
    private static final int MAX_CHUNK_CHARS = 500;

    /** 持久化文件路径（在当前工作目录下） */
    @Value("${spring.ai.vectorstore.simple.persist-path:./data/vectorstore.json}")
    private String persistPath;

    @Autowired
    private ResourceLoader resourceLoader;

    @Autowired
    private VectorStore vectorStore;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        try {
            log.info("[RuleDocInit] 开始初始化平台规则向量库...");

            File file = new File(persistPath);
            // 已存在持久化文件 → 直接 load
            if (file.exists() && file.length() > 0) {
                if (vectorStore instanceof SimpleVectorStore svs) {
                    svs.load(file);
                    log.info("[RuleDocInit] 从本地恢复向量库 | path={}", file.getAbsolutePath());
                    return;
                }
            }

            // 1. 读取 Markdown 原始文本
            Resource resource = resourceLoader.getResource("classpath:ai/platform-rules.md");
            String content = readResource(resource);
            if (content.isEmpty()) {
                log.error("[RuleDocInit] 规则文档为空，跳过");
                return;
            }

            // 2. 按 ## 标题分割章节
            List<String> chunks = splitByHeaders(content);
            log.info("[RuleDocInit] 文档分割为 {} 个片段", chunks.size());

            // 3. 转为 Document 并存入 VectorStore
            List<Document> documents = new ArrayList<>();
            int seq = 0;
            for (String chunk : chunks) {
                String trimmed = chunk.trim();
                if (trimmed.length() < 20) continue;

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("source", SOURCE_TAG);
                metadata.put("seq", seq++);
                metadata.put("charCount", trimmed.length());

                Document doc = new Document(trimmed, metadata);
                documents.add(doc);
            }

            // SimpleVectorStore.add() 会自动完成 embedding + 存内存
            vectorStore.add(documents);

            // 4. 持久化到本地文件
            if (vectorStore instanceof SimpleVectorStore svs) {
                File parent = file.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    log.warn("[RuleDocInit] 无法创建目录 {}", parent.getAbsolutePath());
                }
                svs.save(file);
                log.info("[RuleDocInit] 向量库已持久化 | path={}", file.getAbsolutePath());
            }

            log.info("[RuleDocInit] 完成！共灌入 {} 个规则片段", documents.size());

        } catch (Exception e) {
            log.error("[RuleDocInit] 初始化失败，Spring AI 客服功能将降级运行: {}",
                    e.getMessage(), e);
        }
    }

    private String readResource(Resource resource) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (Exception e) {
            log.warn("[RuleDocInit] 读取资源失败: {}", e.getMessage());
        }
        return sb.toString();
    }

    /**
     * 按 ## 二级标题分割 markdown 文本。
     * 格式：一级用 #，二级用 ##，三级用 ###
     */
    private List<String> splitByHeaders(String content) {
        List<String> chunks = new ArrayList<>();
        String[] lines = content.split("\n");
        StringBuilder current = new StringBuilder();
        boolean inBody = true;

        for (String line : lines) {
            if (line.startsWith("## ")) {
                if (current.length() > 0) {
                    chunks.add(truncate(current.toString()));
                    current.setLength(0);
                }
                current.append(line).append("\n");
                inBody = true;
            } else if (line.startsWith("# ") && current.length() == 0) {
                current.append(line).append("\n");
            } else {
                current.append(line).append("\n");
            }
        }

        if (current.length() > 0) {
            chunks.add(truncate(current.toString()));
        }

        return chunks;
    }

    private String truncate(String text) {
        if (text.length() <= MAX_CHUNK_CHARS) return text;
        return text.substring(0, MAX_CHUNK_CHARS) + "\n...(已截断)";
    }
}