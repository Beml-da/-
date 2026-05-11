package com.example.demo.mapper;

import com.example.demo.entity.FriendRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FriendRequestMapper {

    int insert(FriendRequest request);

    FriendRequest findById(@Param("id") Long id);

    FriendRequest findPendingRequest(@Param("fromUserId") Long fromUserId, @Param("toUserId") Long toUserId);

    List<FriendRequest> findPendingRequestsByToUserId(@Param("toUserId") Long toUserId);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    int deleteById(@Param("id") Long id);
}
