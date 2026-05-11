package com.example.demo.controller;

import com.example.demo.common.Result;
import com.example.demo.dto.FriendRequestVO;
import com.example.demo.dto.FriendVO;
import com.example.demo.service.FriendService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/friend")
public class FriendController {

    @Autowired
    private FriendService friendService;

    @PostMapping("/add")
    public Result<Void> sendFriendRequest(@RequestBody Map<String, String> body) {
        try {
            Long toUserId = Long.valueOf(body.get("toUserId"));
            String message = body.getOrDefault("message", "");
            friendService.sendFriendRequest(toUserId, message);
            return Result.success();
        } catch (NumberFormatException e) {
            return Result.error(400, "无效的用户ID");
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PostMapping("/accept")
    public Result<Void> acceptFriendRequest(@RequestBody Map<String, Long> body) {
        try {
            friendService.acceptFriendRequest(body.get("requestId"));
            return Result.success();
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PostMapping("/reject")
    public Result<Void> rejectFriendRequest(@RequestBody Map<String, Long> body) {
        try {
            friendService.rejectFriendRequest(body.get("requestId"));
            return Result.success();
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    @GetMapping("/list")
    public Result<List<FriendVO>> getMyFriends() {
        try {
            List<FriendVO> friends = friendService.getMyFriends();
            return Result.success(friends);
        } catch (Exception e) {
            return Result.error(500, e.getMessage());
        }
    }

    @DeleteMapping("/delete")
    public Result<Void> deleteFriend(@RequestParam Long friendId) {
        try {
            friendService.deleteFriend(friendId);
            return Result.success();
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    @GetMapping("/search")
    public Result<List<FriendVO>> searchUsers(@RequestParam String keyword) {
        try {
            List<FriendVO> users = friendService.searchUsers(keyword);
            return Result.success(users);
        } catch (Exception e) {
            return Result.error(500, e.getMessage());
        }
    }

    @GetMapping("/requests/pending")
    public Result<List<FriendRequestVO>> getPendingRequests() {
        try {
            List<FriendRequestVO> requests = friendService.getPendingRequests();
            return Result.success(requests);
        } catch (Exception e) {
            return Result.error(500, e.getMessage());
        }
    }

    @GetMapping("/requests/count")
    public Result<Integer> getPendingRequestCount() {
        try {
            int count = friendService.getPendingRequestCount();
            return Result.success(count);
        } catch (Exception e) {
            return Result.error(500, e.getMessage());
        }
    }
}
