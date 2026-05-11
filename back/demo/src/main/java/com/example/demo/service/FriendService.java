package com.example.demo.service;

import com.example.demo.dto.FriendRequestVO;
import com.example.demo.dto.FriendVO;

import java.util.List;

public interface FriendService {

    void sendFriendRequest(Long toUserId, String message);

    void acceptFriendRequest(Long requestId);

    void rejectFriendRequest(Long requestId);

    List<FriendVO> getMyFriends();

    void deleteFriend(Long friendId);

    List<FriendVO> searchUsers(String keyword);

    List<FriendRequestVO> getPendingRequests();

    int getPendingRequestCount();
}
