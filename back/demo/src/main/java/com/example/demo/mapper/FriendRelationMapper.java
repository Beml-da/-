package com.example.demo.mapper;

import com.example.demo.entity.FriendRelation;
import com.example.demo.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FriendRelationMapper {

    int insert(FriendRelation relation);

    int delete(@Param("userId") Long userId, @Param("friendId") Long friendId);

    List<User> findFriendsByUserId(@Param("userId") Long userId);

    FriendRelation findRelation(@Param("userId") Long userId, @Param("friendId") Long friendId);

    FriendRelation findRelationEither(@Param("userId") Long userId, @Param("friendId") Long friendId);
}
