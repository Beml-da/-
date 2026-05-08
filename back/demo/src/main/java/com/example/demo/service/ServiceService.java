package com.example.demo.service;

import com.example.demo.dto.ServiceRequest;
import com.example.demo.entity.Product;
import java.util.List;

public interface ServiceService {

    /**
     * 发布服务
     */
    Product publish(Long providerId, ServiceRequest request);

    /**
     * 分页查询服务列表
     */
    List<Product> list(String keyword, String serviceType, Integer page, Integer pageSize);

    /**
     * 统计服务总数
     */
    Long count(String serviceType);

    /**
     * 根据关键词统计服务总数
     */
    Long countByKeyword(String keyword, String serviceType);

    /**
     * 获取服务详情
     */
    Product getById(Long id);

    /**
     * 获取用户发布的服务
     */
    List<Product> getByProviderId(Long providerId);

    /**
     * 更新服务
     */
    Product update(Long id, Long providerId, ServiceRequest request);

    /**
     * 删除服务
     */
    void delete(Long id, Long providerId);

    /**
     * 更新服务状态
     */
    void updateStatus(Long id, Long providerId, String status);
}
