package com.example.demo.service;

import com.example.demo.dto.ProductRequest;
import com.example.demo.entity.Product;
import java.util.List;
import java.util.Map;

public interface ProductService {

    /**
     * 发布商品
     */
    Product publish(Long sellerId, ProductRequest request);

    /**
     * 分页查询商品列表
     */
    List<Product> list(String keyword, String category, String condition,
                       Double minPrice, Double maxPrice, String sortBy,
                       Integer page, Integer pageSize);

    /**
     * 统计商品总数
     */
    Long count(String keyword, String category, String condition,
               Double minPrice, Double maxPrice);

    /**
     * 获取商品详情
     */
    Product getById(Long id);

    /**
     * 获取热门商品
     */
    List<Product> getHotProducts(Integer limit);

    /**
     * 获取最新商品
     */
    List<Product> getNewestProducts(Integer limit);

    /**
     * 获取最新发布（含商品和服务）
     */
    List<Product> getNewestAllProducts(Integer limit);

    /**
     * 获取卖家发布的商品
     */
    List<Product> getBySellerId(Long sellerId);

    /**
     * 获取卖家发布的指定类型商品
     */
    List<Product> getBySellerIdAndType(Long sellerId, String type);

    /**
     * 更新商品
     */
    Product update(Long id, Long sellerId, ProductRequest request);

    /**
     * 更新商品状态
     */
    void updateStatus(Long id, Long sellerId, String status);

    /**
     * 删除商品
     */
    void delete(Long id, Long sellerId);

    /**
     * 增加浏览量
     */
    void incrementViewCount(Long id);

    /**
     * 初始化浏览量（模拟真实浏览数据）
     */
    void initViewCounts();

    /**
     * 搜索建议（按名称去重）
     */
    List<Map<String, Object>> searchSuggestions(String keyword, Integer limit);

    /**
     * 统一搜索（商品+服务）
     */
    List<Product> searchAll(String keyword, String sortBy, Integer page, Integer pageSize);

    /**
     * 统一搜索总数
     */
    Long countSearchAll(String keyword);
}
