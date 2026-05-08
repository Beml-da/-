package com.example.demo.mapper;

import com.example.demo.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface ProductMapper {

    /**
     * 根据ID查询
     */
    Product findById(@Param("id") Long id);

    /**
     * 条件分页查询商品列表
     */
    List<Product> findList(@Param("keyword") String keyword,
                           @Param("category") String category,
                           @Param("condition") String condition,
                           @Param("minPrice") Double minPrice,
                           @Param("maxPrice") Double maxPrice,
                           @Param("sortBy") String sortBy,
                           @Param("offset") Integer offset,
                           @Param("limit") Integer limit);

    /**
     * 统计商品总数
     */
    Long count(@Param("keyword") String keyword,
               @Param("category") String category,
               @Param("condition") String condition,
               @Param("minPrice") Double minPrice,
               @Param("maxPrice") Double maxPrice);

    /**
     * 根据卖家ID查询
     */
    List<Product> findBySellerId(@Param("sellerId") Long sellerId);

    /**
     * 根据卖家ID和类型查询
     */
    List<Product> findBySellerIdAndType(@Param("sellerId") Long sellerId, @Param("type") String type);

    /**
     * 热门商品（按收藏数排序）
     */
    List<Product> findHot(@Param("limit") Integer limit);

    /**
     * 最新商品（按创建时间排序）
     */
    List<Product> findNewest(@Param("limit") Integer limit);

    /**
     * 最新发布（仅商品，不含服务）
     */
    List<Product> findNewestAll(@Param("limit") Integer limit);

    /**
     * 服务列表分页查询
     */
    List<Product> findServiceList(@Param("keyword") String keyword,
                                  @Param("serviceType") String serviceType,
                                  @Param("offset") Integer offset,
                                  @Param("limit") Integer limit);

    /**
     * 统计服务总数
     */
    Long countServiceList(@Param("keyword") String keyword,
                           @Param("serviceType") String serviceType);

    /**
     * 新增商品
     */
    int insert(Product product);

    /**
     * 更新商品
     */
    int update(Product product);

    /**
     * 更新商品状态
     */
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /**
     * 删除商品
     */
    int deleteById(@Param("id") Long id);

    /**
     * 浏览量+1
     */
    int incrementViewCount(@Param("id") Long id);

    /**
     * 初始化浏览量（模拟真实浏览数据）
     */
    int initViewCounts();

    /**
     * 搜索建议（按名称去重）
     */
    List<Map<String, Object>> searchSuggestions(@Param("keyword") String keyword, @Param("limit") Integer limit);

    /**
     * 统一搜索（商品+服务）
     */
    List<Product> searchAll(@Param("keyword") String keyword,
                           @Param("sortBy") String sortBy,
                           @Param("offset") Integer offset,
                           @Param("limit") Integer limit);

    /**
     * 统一搜索总数
     */
    Long countSearchAll(@Param("keyword") String keyword);
}
