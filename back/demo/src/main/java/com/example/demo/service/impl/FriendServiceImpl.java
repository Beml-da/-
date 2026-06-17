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
import com.example.demo.websocket.ChatWebSocketUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;

@Service
public class FriendServiceImpl implements FriendService {

    private static final Logger log = LoggerFactory.getLogger(FriendServiceImpl.class);

    private static final int PROFILE_TTL = 10;
    private static final int FRIEND_LIST_TTL = 5;
    private static final int PENDING_TTL = 2;

    @Autowired
    private FriendRequestMapper friendRequestMapper;

    @Autowired
    private FriendRelationMapper friendRelationMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ChatWebSocketUtils chatWebSocketUtils;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Override
    @Transactional
    public void sendFriendRequest(Long toUserId, String message) {
        Long currentUserId = getCurrentUserIdFromContext();

        if (currentUserId.equals(toUserId)) {
            throw new RuntimeException("不能添加自己为好友");
        }

        User toUser = getUserProfile(toUserId);
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

        evictPendingCache(toUserId);
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

        upsertFriendRelation(currentUserId, request.getFromUserId());
        upsertFriendRelation(request.getFromUserId(), currentUserId);

        evictPendingCache(currentUserId);
        evictFriendListCache(currentUserId);
        evictFriendListCache(request.getFromUserId());
    }

    private void upsertFriendRelation(Long userId, Long friendId) {
        FriendRelation existing = friendRelationMapper.findRelation(userId, friendId);
        if (existing != null) {
            friendRelationMapper.restore(userId, friendId);
        } else {
            friendRelationMapper.insert(new FriendRelation(userId, friendId));
        }
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
        String key = "friend:list:" + currentUserId;

        List<FriendVO> friends = null;
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                friends = objectMapper.readValue(cached, new TypeReference<List<FriendVO>>() {});
            }
        } catch (Exception e) {
            log.error("Redis GET 失败, key={}", key, e);
        }

        if (friends == null) {
            List<User> userFriends = friendRelationMapper.findFriendsByUserId(currentUserId);
            friends = userFriends.stream()
                    .map(user -> new FriendVO(user, chatWebSocketUtils.isUserOnline(user.getId())))
                    .collect(Collectors.toList());
        } else {
            // 缓存命中：仅刷新 online 字段（不要直接 return，务必把最新结果写回缓存）
            for (FriendVO friend : friends) {
                friend.setOnline(chatWebSocketUtils.isUserOnline(friend.getId()));
            }
        }

        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(friends), FRIEND_LIST_TTL, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("Redis SET 失败, key={}", key, e);
        }

        return friends;
    }

    @Override
    @Transactional
    public void deleteFriend(Long friendId) {
        Long currentUserId = getCurrentUserIdFromContext();
        friendRelationMapper.delete(currentUserId, friendId);
        friendRelationMapper.delete(friendId, currentUserId);

        evictFriendListCache(currentUserId);
        evictFriendListCache(friendId);

        try {
            Map<String, Object> data = new HashMap<>();
            data.put("type", "friend-removed");
            data.put("data", java.util.Collections.singletonMap("removedBy", currentUserId));
            String json = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                    .writeValueAsString(data);
            if (chatWebSocketUtils.isUserOnline(friendId)) {
                chatWebSocketUtils.sendMessageToOnlineUser(friendId, json);
            }
        } catch (Exception e) {
            System.out.println("[FriendService] 推送 friend-removed 失败: " + e.getMessage());
        }
    }

    @Override
    public List<FriendVO> searchUsers(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return List.of();
        }
        List<User> users = userMapper.searchByKeyword(keyword.trim());
        return users.stream()
                .map(user -> new FriendVO(user, chatWebSocketUtils.isUserOnline(user.getId())))
                .collect(Collectors.toList());
    }

    @Override
    public List<FriendRequestVO> getPendingRequests() {
        Long currentUserId = getCurrentUserIdFromContext();
        String key = "friend:pending:" + currentUserId;

        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return objectMapper.readValue(cached, new TypeReference<List<FriendRequestVO>>() {});
            }
        } catch (Exception e) {
            log.error("Redis GET 失败, key={}", key, e);
        }

        List<FriendRequest> requests = friendRequestMapper.findPendingRequestsByToUserId(currentUserId);
        List<FriendRequestVO> result = requests.stream().map(req -> {
            User fromUser = getUserProfile(req.getFromUserId());
            if (fromUser != null) {
                return new FriendRequestVO(req, fromUser);
            }
            return null;
        }).filter(java.util.Objects::nonNull).collect(Collectors.toList());

        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(result), PENDING_TTL, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("Redis SET 失败, key={}", key, e);
        }

        return result;
    }

    @Override
    public int getPendingRequestCount() {
        Long currentUserId = getCurrentUserIdFromContext();
        return friendRequestMapper.findPendingRequestsByToUserId(currentUserId).size();
    }

    private Long getCurrentUserIdFromContext() {
        return UserContext.getCurrentUserId();
    }

    private User getUserProfile(Long userId) {
        String key = "user:profile:" + userId;
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return objectMapper.readValue(cached, User.class);
            }
        } catch (Exception e) {
            log.error("Redis GET 失败, key={}", key, e);
        }
        User user = userMapper.findById(userId);
        if (user != null) {
            try {
                redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(user), PROFILE_TTL, TimeUnit.MINUTES);
            } catch (Exception e) {
                log.error("Redis SET 失败, key={}", key, e);
            }
        }
        return user;
    }

    private void evictFriendListCache(Long userId) {
        try {
            redisTemplate.delete("friend:list:" + userId);
        } catch (Exception e) {
            log.error("删除好友列表缓存失败, userId={}", userId, e);
        }
    }

    private void evictPendingCache(Long userId) {
        try {
            redisTemplate.delete("friend:pending:" + userId);
        } catch (Exception e) {
            log.error("删除待处理请求缓存失败, userId={}", userId, e);
        }
    }
}
