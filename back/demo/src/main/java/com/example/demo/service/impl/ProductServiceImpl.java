package com.example.demo.service.impl;

import com.example.demo.dto.ProductRequest;
import com.example.demo.entity.Product;
import com.example.demo.mapper.ProductMapper;
import com.example.demo.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class ProductServiceImpl implements ProductService {

    @Autowired
    private ProductMapper productMapper;

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
        return product;
    }

    @Override
    public List<Product> list(String keyword, String category, String condition,
                             Double minPrice, Double maxPrice, String sortBy,
                             Integer page, Integer pageSize) {
        int offset = (page - 1) * pageSize;
        return productMapper.findList(keyword, category, condition, minPrice, maxPrice, sortBy, offset, pageSize);
    }

    @Override
    public Long count(String keyword, String category, String condition,
                      Double minPrice, Double maxPrice) {
        return productMapper.count(keyword, category, condition, minPrice, maxPrice);
    }

    @Override
    public Product getById(Long id) {
        return productMapper.findById(id);
    }

    @Override
    public List<Product> getHotProducts(Integer limit) {
        return productMapper.findHot(limit);
    }

    @Override
    public List<Product> getNewestProducts(Integer limit) {
        return productMapper.findNewest(limit);
    }

    @Override
    public List<Product> getNewestAllProducts(Integer limit) {
        return productMapper.findNewestAll(limit);
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
    }

    @Override
    public void incrementViewCount(Long id) {
        productMapper.incrementViewCount(id);
    }

    @Override
    public void initViewCounts() {
        productMapper.initViewCounts();
    }

    @Override
    public List<Map<String, Object>> searchSuggestions(String keyword, Integer limit) {
        return productMapper.searchSuggestions(keyword, limit);
    }

    @Override
    public List<Product> searchAll(String keyword, String sortBy, Integer page, Integer pageSize) {
        int offset = (page - 1) * pageSize;
        return productMapper.searchAll(keyword, sortBy, offset, pageSize);
    }

    @Override
    public Long countSearchAll(String keyword) {
        return productMapper.countSearchAll(keyword);
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
