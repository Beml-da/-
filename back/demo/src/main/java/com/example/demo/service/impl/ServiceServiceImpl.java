package com.example.demo.service.impl;

import com.example.demo.dto.ServiceRequest;
import com.example.demo.entity.Product;
import com.example.demo.mapper.ProductMapper;
import com.example.demo.service.ServiceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ServiceServiceImpl implements ServiceService {

    @Autowired
    private ProductMapper productMapper;

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
        return service;
    }

    @Override
    public List<Product> list(String keyword, String serviceType, Integer page, Integer pageSize) {
        int offset = (page - 1) * pageSize;
        return productMapper.findServiceList(keyword, serviceType, offset, pageSize);
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
        return productMapper.findById(id);
    }

    @Override
    public List<Product> getByProviderId(Long providerId) {
        return productMapper.findBySellerIdAndType(providerId, "service");
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
