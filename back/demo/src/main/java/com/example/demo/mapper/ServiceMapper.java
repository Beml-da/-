package com.example.demo.mapper;

import com.example.demo.entity.CampusService;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ServiceMapper {

    /**
     * 根据ID查询
     */
    CampusService findById(@Param("id") Long id);

    /**
     * 条件分页查询
     */
    List<CampusService> findList(@Param("keyword") String keyword,
                                @Param("serviceType") String serviceType,
                                @Param("offset") Integer offset,
                                @Param("limit") Integer limit);

    /**
     * 统计总数
     */
    Long count(@Param("serviceType") String serviceType);

    /**
     * 根据关键词统计总数
     */
    Long countByKeyword(@Param("keyword") String keyword, @Param("serviceType") String serviceType);

    /**
     * 根据提供者ID查询
     */
    List<CampusService> findByProviderId(@Param("providerId") Long providerId);

    /**
     * 新增服务
     */
    int insert(CampusService service);

    /**
     * 更新服务
     */
    int update(CampusService service);

    /**
     * 删除服务
     */
    int deleteById(@Param("id") Long id);
}
