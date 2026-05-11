package com.example.demo.service.impl;

import com.example.demo.dto.FriendRequestVO;
import com.example.demo.dto.FriendVO;
import com.example.demo.entity.FriendRelation;
import com.example.demo.entity.FriendRequest;
import com.example.demo.entity.User;
import com.example.demo.mapper.FriendRelationMapper;
import com.example.demo.mapper.FriendRequestMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.FriendService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class FriendServiceImpl implements FriendService {

    @Autowired
    private FriendRequestMapper friendRequestMapper;

    @Autowired
    private FriendRelationMapper friendRelationMapper;

    @Autowired
    private UserMapper userMapper;

    @Override
    @Transactional
    public void sendFriendRequest(Long toUserId, String message) {
        Long currentUserId = getCurrentUserIdFromContext();

        if (currentUserId.equals(toUserId)) {
            throw new RuntimeException("不能添加自己为好友");
        }

        User toUser = userMapper.findById(toUserId);
        if (toUser == null) {
            throw new RuntimeException("用户不存在");
        }

        FriendRelation existing = friendRelationMapper.findRelationEither(currentUserId, toUserId);
        if (existing != null) {
            throw new RuntimeException("你们已经是好友了");
        }

        FriendRequest pending = friendRequestMapper.findPendingRequest(currentUserId, toUserId);
        if (pending != null) {
            throw new RuntimeException("已发送过好友请求，请等待对方处理");
        }

        FriendRequest reverse = friendRequestMapper.findPendingRequest(toUserId, currentUserId);
        if (reverse != null) {
            throw new RuntimeException("对方已向你发送过好友请求，请先处理");
        }

        FriendRequest request = new FriendRequest(currentUserId, toUserId, message);
        friendRequestMapper.insert(request);
    }

    @Override
    @Transactional
    public void acceptFriendRequest(Long requestId) {
        Long currentUserId = getCurrentUserIdFromContext();

        FriendRequest request = friendRequestMapper.findById(requestId);
        if (request == null) {
            throw new RuntimeException("请求不存在");
        }

        if (!request.getToUserId().equals(currentUserId)) {
            throw new RuntimeException("无权操作此请求");
        }

        if (request.getStatus() != 0) {
            throw new RuntimeException("该请求已处理");
        }

        friendRequestMapper.updateStatus(requestId, 1);

        friendRelationMapper.insert(new FriendRelation(currentUserId, request.getFromUserId()));
        friendRelationMapper.insert(new FriendRelation(request.getFromUserId(), currentUserId));
    }

    @Override
    @Transactional
    public void rejectFriendRequest(Long requestId) {
        Long currentUserId = getCurrentUserIdFromContext();

        FriendRequest request = friendRequestMapper.findById(requestId);
        if (request == null) {
            throw new RuntimeException("请求不存在");
        }

        if (!request.getToUserId().equals(currentUserId)) {
            throw new RuntimeException("无权操作此请求");
        }

        if (request.getStatus() != 0) {
            throw new RuntimeException("该请求已处理");
        }

        friendRequestMapper.updateStatus(requestId, 2);
    }

    @Override
    public List<FriendVO> getMyFriends() {
        Long currentUserId = getCurrentUserIdFromContext();
        List<User> friends = friendRelationMapper.findFriendsByUserId(currentUserId);
        return friends.stream().map(FriendVO::new).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteFriend(Long friendId) {
        Long currentUserId = getCurrentUserIdFromContext();
        friendRelationMapper.delete(currentUserId, friendId);
        friendRelationMapper.delete(friendId, currentUserId);
    }

    @Override
    public List<FriendVO> searchUsers(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return List.of();
        }
        List<User> users = userMapper.searchByKeyword(keyword.trim());
        return users.stream().map(FriendVO::new).collect(Collectors.toList());
    }

    @Override
    public List<FriendRequestVO> getPendingRequests() {
        Long currentUserId = getCurrentUserIdFromContext();
        List<FriendRequest> requests = friendRequestMapper.findPendingRequestsByToUserId(currentUserId);
        return requests.stream().map(req -> {
            User fromUser = userMapper.findById(req.getFromUserId());
            if (fromUser != null) {
                return new FriendRequestVO(req, fromUser);
            }
            return null;
        }).filter(java.util.Objects::nonNull).collect(Collectors.toList());
    }

    @Override
    public int getPendingRequestCount() {
        Long currentUserId = getCurrentUserIdFromContext();
        return friendRequestMapper.findPendingRequestsByToUserId(currentUserId).size();
    }

    private Long getCurrentUserIdFromContext() {
        return UserContext.getCurrentUserId();
    }
}
