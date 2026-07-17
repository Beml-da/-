package com.example.demo.service.impl;

import com.example.demo.dto.ProductRequest;
import com.example.demo.entity.Product;
import com.example.demo.mapper.ProductMapper;
import com.example.demo.service.ProductService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class ProductServiceImpl implements ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductServiceImpl.class);

    private static final int HOT_TTL = 5;
    private static final int NEWEST_TTL = 2;
    private static final int DETAIL_TTL = 10;
    private static final int SUGGESTION_TTL = 10;
    private static final int LIST_TTL = 5;
    private static final int SEARCH_TTL = 5;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Override
    @Transactional
    public Product publish(Long sellerId, ProductRequest request) {
        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("请填写商品名称");
        }
        if (request.getPrice() == null) {
            throw new IllegalArgumentException("请填写商品价格");
        }
        if (request.getCategoryId() == null || request.getCategoryId().trim().isEmpty()) {
            throw new IllegalArgumentException("请选择商品分类");
        }
        if (request.getCondition() == null || request.getCondition().trim().isEmpty()) {
            throw new IllegalArgumentException("请选择商品成色");
        }
        Product product = new Product();
        product.setTitle(request.getTitle());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setOriginalPrice(request.getOriginalPrice());
        product.setImages(toJson(request.getImages()));
        if (request.getCategoryId() != null && !request.getCategoryId().trim().isEmpty()) {
            product.setCategoryId(request.getCategoryId());
        }
        product.setSubCategory(request.getSubCategory());
        if (request.getCondition() != null) {
            product.setCondition(request.getCondition());
        }
        product.setLocation(request.getLocation());
        product.setIsNegotiable(request.getIsNegotiable() != null ? request.getIsNegotiable() : 1);
        product.setTags(toJson(request.getTags()));
        product.setSellerId(sellerId);
        product.setStatus("在售");
        product.setType("product");
        productMapper.insert(product);

        evictHotCache();
        evictNewestCache();
        evictListCache();
        evictSearchCache();
        addSuggestionWord(request.getTitle());

        return product;
    }

    @Override
    public List<Product> list(String keyword, String category, String condition,
                             Double minPrice, Double maxPrice, String sortBy,
                             Integer page, Integer pageSize) {
        String key = buildListCacheKey(keyword, category, condition, minPrice, maxPrice, sortBy, page, pageSize);
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return objectMapper.readValue(cached, new TypeReference<List<Product>>() {});
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        int offset = (page - 1) * pageSize;
        List<Product> list = productMapper.findList(keyword, category, condition, minPrice, maxPrice, sortBy, offset, pageSize);
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(list), LIST_TTL, TimeUnit.MINUTES);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public Long count(String keyword, String category, String condition,
                      Double minPrice, Double maxPrice) {
        return productMapper.count(keyword, category, condition, minPrice, maxPrice);
    }

    @Override
    public Product getById(Long id) {
        String key = "product:" + id;
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return objectMapper.readValue(cached, Product.class);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        Product product = productMapper.findById(id);
        if (product != null) {
            try {
                redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(product), DETAIL_TTL, TimeUnit.MINUTES);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return product;
    }

    @Override
    public List<Product> getHotProducts(Integer limit) {
        String key = "product:hot:" + limit;
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return objectMapper.readValue(cached, new TypeReference<List<Product>>() {});
            }
        } catch (Exception e) {
            log.error("Redis GET失败, key={}", key, e);
        }
        List<Product> products = productMapper.findHot(limit);
        try {
            String json = objectMapper.writeValueAsString(products);
            redisTemplate.opsForValue().set(key, json, HOT_TTL, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("Redis SET失败, key={}", key, e);
        }
        return products;
    }

    @Override
    public List<Product> getNewestProducts(Integer limit) {
        String key = "product:newest:" + limit;
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return objectMapper.readValue(cached, new TypeReference<List<Product>>() {});
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        List<Product> products = productMapper.findNewest(limit);
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(products), NEWEST_TTL, TimeUnit.MINUTES);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return products;
    }

    @Override
    public List<Product> getNewestAllProducts(Integer limit) {
        String key = "product:newest:all:" + limit;
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return objectMapper.readValue(cached, new TypeReference<List<Product>>() {});
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        List<Product> products = productMapper.findNewestAll(limit);
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(products), NEWEST_TTL, TimeUnit.MINUTES);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return products;
    }

    @Override
    public List<Product> getBySellerId(Long sellerId) {
        return productMapper.findBySellerId(sellerId);
    }

    @Override
    public List<Product> getBySellerIdAndType(Long sellerId, String type) {
        return productMapper.findBySellerIdAndType(sellerId, type);
    }

    @Override
    @Transactional
    public Product update(Long id, Long sellerId, ProductRequest request) {
        Product existing = productMapper.findById(id);
        if (existing == null) {
            throw new RuntimeException("商品不存在");
        }
        if (!existing.getSellerId().equals(sellerId)) {
            throw new RuntimeException("无权修改此商品");
        }

        Product product = new Product();
        product.setId(id);
        product.setTitle(request.getTitle());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setOriginalPrice(request.getOriginalPrice());
        product.setImages(toJson(request.getImages()));
        product.setCategoryId(request.getCategoryId());
        product.setSubCategory(request.getSubCategory());
        product.setCondition(request.getCondition());
        product.setLocation(request.getLocation());
        product.setIsNegotiable(request.getIsNegotiable());
        product.setTags(toJson(request.getTags()));
        productMapper.update(product);

        redisTemplate.delete("product:" + id);
        evictHotCache();
        evictNewestCache();
        evictListCache();
        evictSearchCache();

        return productMapper.findById(id);
    }

    @Override
    @Transactional
    public void updateStatus(Long id, Long sellerId, String status) {
        Product existing = productMapper.findById(id);
        if (existing == null) {
            throw new RuntimeException("商品不存在");
        }
        if (!existing.getSellerId().equals(sellerId)) {
            throw new RuntimeException("无权修改此商品");
        }
        productMapper.updateStatus(id, status);

        redisTemplate.delete("product:" + id);
        if ("已下架".equals(status) || "已删除".equals(status)) {
            evictHotCache();
            evictNewestCache();
            evictListCache();
            evictSearchCache();
        }
    }

    @Override
    @Transactional
    public void delete(Long id, Long sellerId) {
        Product existing = productMapper.findById(id);
        if (existing == null) {
            throw new RuntimeException("商品不存在");
        }
        if (!existing.getSellerId().equals(sellerId)) {
            throw new RuntimeException("无权删除此商品");
        }
        productMapper.deleteById(id);

        redisTemplate.delete("product:" + id);
        evictHotCache();
        evictNewestCache();
        evictListCache();
        evictSearchCache();
    }

    @Override
    public void incrementViewCount(Long id) {
        productMapper.incrementViewCount(id);
        redisTemplate.delete("product:" + id);
        evictHotCache();
    }

    @Override
    public void initViewCounts() {
        productMapper.initViewCounts();
        evictHotCache();
    }

    @Override
    public List<Map<String, Object>> searchSuggestions(String keyword, Integer limit) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return List.of();
        }
        String key = "suggestion:" + keyword.trim().toLowerCase();
        try {
            String cachedVal = redisTemplate.opsForValue().get(key);
            if (cachedVal != null) {
                List<Map<String, Object>> result = objectMapper.readValue(cachedVal,
                    new TypeReference<List<Map<String, Object>>>() {});
                if (result.size() <= limit) {
                    return result;
                }
                return result.subList(0, limit);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        List<Map<String, Object>> suggestions = productMapper.searchSuggestions(keyword, limit);
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(suggestions),
                SUGGESTION_TTL, TimeUnit.MINUTES);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return suggestions;
    }

    @Override
    public List<Product> searchAll(String keyword, String sortBy, Integer page, Integer pageSize) {
        String key = buildSearchCacheKey(keyword, sortBy, page, pageSize);
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return objectMapper.readValue(cached, new TypeReference<List<Product>>() {});
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        int offset = (page - 1) * pageSize;
        List<Product> list = productMapper.searchAll(keyword, sortBy, offset, pageSize);
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(list), SEARCH_TTL, TimeUnit.MINUTES);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public Long countSearchAll(String keyword) {
        return productMapper.countSearchAll(keyword);
    }

    private void evictHotCache() {
        try {
            Set<String> keys = redisTemplate.keys("product:hot:*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void evictNewestCache() {
        try {
            Set<String> keys = redisTemplate.keys("product:newest:*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void addSuggestionWord(String title) {
        if (title == null || title.trim().isEmpty()) return;
        String key = "suggestion:" + title.trim().toLowerCase().split("\\s+")[0];
        try {
            redisTemplate.opsForSet().add(key, title);
            redisTemplate.expire(key, SUGGESTION_TTL, TimeUnit.MINUTES);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String buildListCacheKey(String keyword, String category, String condition,
                                     Double minPrice, Double maxPrice, String sortBy,
                                     Integer page, Integer pageSize) {
        return String.format("product:list:%s:%s:%s:%s:%s:%s:%d:%d",
                nvl(keyword), nvl(category), nvl(condition),
                minPrice != null ? minPrice.toString() : "",
                maxPrice != null ? maxPrice.toString() : "",
                nvl(sortBy), page, pageSize);
    }

    private String buildSearchCacheKey(String keyword, String sortBy, Integer page, Integer pageSize) {
        return String.format("product:search:%s:%s:%d:%d",
                nvl(keyword), nvl(sortBy), page, pageSize);
    }

    private String nvl(String s) {
        return s != null ? s : "";
    }

    private void evictListCache() {
        try {
            Set<String> keys = redisTemplate.keys("product:list:*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void evictSearchCache() {
        try {
            Set<String> keys = redisTemplate.keys("product:search:*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void evictAllCaches() {
        evictHotCache();
        evictNewestCache();
        evictListCache();
        evictSearchCache();
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        if (obj instanceof String) return (String) obj;
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                sb.append("\"").append(escapeJson(String.valueOf(list.get(i)))).append("\"");
                if (i < list.size() - 1) sb.append(",");
            }
            sb.append("]");
            return sb.toString();
        }
        return null;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
