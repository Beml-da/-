package com.example.demo.service.impl;

import com.example.demo.dto.ServiceRequest;
import com.example.demo.entity.Product;
import com.example.demo.mapper.ProductMapper;
import com.example.demo.service.ServiceService;
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
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class ServiceServiceImpl implements ServiceService {

    private static final Logger log = LoggerFactory.getLogger(ServiceServiceImpl.class);

    private static final int DETAIL_TTL = 10;
    private static final int LIST_TTL = 5;
    private static final int HOT_TTL = 5;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Override
    @Transactional
    public Product publish(Long providerId, ServiceRequest request) {
        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("请填写服务标题");
        }
        if (request.getDescription() == null || request.getDescription().trim().isEmpty()) {
            throw new IllegalArgumentException("请填写服务详情");
        }
        if (request.getPrice() == null) {
            throw new IllegalArgumentException("请填写服务价格");
        }
        if (request.getServiceType() == null || request.getServiceType().trim().isEmpty()) {
            throw new IllegalArgumentException("请选择服务类型");
        }
        Product service = new Product();
        service.setTitle(request.getTitle().trim());
        service.setDescription(request.getDescription().trim());
        service.setPrice(request.getPrice());
        service.setLocation(request.getLocation());
        service.setTags(toJson(request.getTags()));
        service.setSellerId(providerId);
        service.setStatus("可用");
        service.setType("service");
        service.setServiceType(request.getServiceType());
        service.setPriceUnit(request.getPriceUnit() != null ? request.getPriceUnit() : "/次");
        service.setIsNegotiable(request.getIsNegotiable() != null ? request.getIsNegotiable() : 0);
        productMapper.insert(service);

        evictDetailCache(service.getId());
        evictListCache();
        evictHotCache();

        return service;
    }

    @Override
    public List<Product> list(String keyword, String serviceType, Integer page, Integer pageSize) {
        String key = buildListCacheKey(keyword, serviceType, page, pageSize);
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return objectMapper.readValue(cached, new TypeReference<List<Product>>() {});
            }
        } catch (Exception e) {
            log.error("Redis GET 失败, key={}", key, e);
        }
        int offset = (page - 1) * pageSize;
        List<Product> list = productMapper.findServiceList(keyword, serviceType, offset, pageSize);
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(list), LIST_TTL, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("Redis SET 失败, key={}", key, e);
        }
        return list;
    }

    @Override
    public Long count(String serviceType) {
        return productMapper.countServiceList(null, serviceType);
    }

    @Override
    public Long countByKeyword(String keyword, String serviceType) {
        return productMapper.countServiceList(keyword, serviceType);
    }

    @Override
    public Product getById(Long id) {
        String key = "service:" + id;
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return objectMapper.readValue(cached, Product.class);
            }
        } catch (Exception e) {
            log.error("Redis GET 失败, key={}", key, e);
        }
        Product service = productMapper.findById(id);
        if (service != null) {
            try {
                redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(service), DETAIL_TTL, TimeUnit.MINUTES);
            } catch (Exception e) {
                log.error("Redis SET 失败, key={}", key, e);
            }
        }
        return service;
    }

    @Override
    public List<Product> getByProviderId(Long providerId) {
        return productMapper.findBySellerIdAndType(providerId, "service");
    }

    @Override
    public List<Product> getHotServices(Integer limit) {
        String key = "service:hot:" + limit;
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return objectMapper.readValue(cached, new TypeReference<List<Product>>() {});
            }
        } catch (Exception e) {
            log.error("Redis GET 失败, key={}", key, e);
        }
        List<Product> services = productMapper.findHotServices(limit);
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(services), HOT_TTL, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("Redis SET 失败, key={}", key, e);
        }
        return services;
    }

    @Override
    @Transactional
    public Product update(Long id, Long providerId, ServiceRequest request) {
        Product existing = productMapper.findById(id);
        if (existing == null) {
            throw new RuntimeException("服务不存在");
        }
        if (!existing.getSellerId().equals(providerId)) {
            throw new RuntimeException("无权修改此服务");
        }
        if (request.getTitle() != null && request.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("请填写服务标题");
        }
        if (request.getDescription() != null && request.getDescription().trim().isEmpty()) {
            throw new IllegalArgumentException("请填写服务详情");
        }

        Product service = new Product();
        service.setId(id);
        service.setTitle(request.getTitle() != null ? request.getTitle().trim() : null);
        service.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
        service.setPrice(request.getPrice());
        service.setLocation(request.getLocation());
        service.setTags(toJson(request.getTags()));
        service.setServiceType(request.getServiceType());
        service.setPriceUnit(request.getPriceUnit());
        service.setIsNegotiable(request.getIsNegotiable());
        productMapper.update(service);

        evictDetailCache(id);
        evictListCache();
        evictHotCache();

        return productMapper.findById(id);
    }

    @Override
    @Transactional
    public void delete(Long id, Long providerId) {
        Product existing = productMapper.findById(id);
        if (existing == null) {
            throw new RuntimeException("服务不存在");
        }
        if (!existing.getSellerId().equals(providerId)) {
            throw new RuntimeException("无权删除此服务");
        }
        productMapper.deleteById(id);

        evictDetailCache(id);
        evictListCache();
        evictHotCache();
    }

    @Override
    @Transactional
    public void updateStatus(Long id, Long providerId, String status) {
        Product existing = productMapper.findById(id);
        if (existing == null) {
            throw new RuntimeException("服务不存在");
        }
        if (!existing.getSellerId().equals(providerId)) {
            throw new RuntimeException("无权修改此服务");
        }
        productMapper.updateStatus(id, status);

        evictDetailCache(id);
        if ("已删除".equals(status) || "已下架".equals(status) || "不可用".equals(status)) {
            evictListCache();
            evictHotCache();
        }
    }

    private void evictDetailCache(Long id) {
        try {
            redisTemplate.delete("service:" + id);
        } catch (Exception e) {
            log.error("删除服务缓存失败, id={}", id, e);
        }
    }

    private void evictListCache() {
        try {
            Set<String> keys = redisTemplate.keys("service:list:*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.error("删除服务列表缓存失败", e);
        }
    }

    private void evictHotCache() {
        try {
            Set<String> keys = redisTemplate.keys("service:hot:*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.error("删除热门服务缓存失败", e);
        }
    }

    private String buildListCacheKey(String keyword, String serviceType, Integer page, Integer pageSize) {
        return String.format("service:list:%s:%s:%d:%d",
                nvl(keyword), nvl(serviceType), page, pageSize);
    }

    private String nvl(String s) {
        return s != null ? s : "";
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
