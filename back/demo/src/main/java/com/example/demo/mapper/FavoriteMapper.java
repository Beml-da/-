package com.example.demo.mapper;

import com.example.demo.entity.Favorite;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface FavoriteMapper {

    @Select("SELECT f.*, p.title as product_title, p.price as product_price, " +
            "p.images as product_images, p.status as product_status, " +
            "p.seller_id as product_seller_id " +
            "FROM sys_favorite f " +
            "LEFT JOIN sys_product p ON p.id = f.product_id AND p.deleted = 0 " +
            "WHERE f.user_id = #{userId} " +
            "ORDER BY f.create_time DESC")
    List<Favorite> findByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM sys_favorite WHERE user_id = #{userId} AND product_id = #{productId}")
    Favorite findByUserAndProduct(@Param("userId") Long userId, @Param("productId") Long productId);

    @Select("SELECT COUNT(*) FROM sys_favorite WHERE user_id = #{userId} AND product_id = #{productId}")
    int exists(@Param("userId") Long userId, @Param("productId") Long productId);

    @Insert("INSERT INTO sys_favorite (user_id, product_id, create_time) VALUES (#{userId}, #{productId}, NOW())")
    int insert(Favorite favorite);

    @Delete("DELETE FROM sys_favorite WHERE user_id = #{userId} AND product_id = #{productId}")
    int deleteByUserAndProduct(@Param("userId") Long userId, @Param("productId") Long productId);

    @Select("SELECT COUNT(*) FROM sys_favorite WHERE user_id = #{userId}")
    int countByUserId(@Param("userId") Long userId);
}
