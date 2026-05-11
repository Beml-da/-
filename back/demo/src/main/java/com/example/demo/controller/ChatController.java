package com.example.demo.controller;

import com.example.demo.common.Result;
import com.example.demo.dto.ChatMessageVO;
import com.example.demo.dto.ChatSessionVO;
import com.example.demo.service.ChatService;
import com.example.demo.service.impl.UserContext;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/history")
    public Result<List<ChatMessageVO>> getHistory(
            @RequestParam Long targetUserId,
            @RequestParam(defaultValue = "50") Integer limit) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) return Result.error("未登录");
        List<ChatMessageVO> history = chatService.getHistory(userId, targetUserId);
        return Result.success(history);
    }

    @GetMapping("/sessions")
    public Result<List<ChatSessionVO>> getSessions() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) return Result.error("未登录");
        List<ChatSessionVO> sessions = chatService.getSessions(userId);
        return Result.success(sessions);
    }

    @PostMapping("/read")
    public Result<Void> markRead(@RequestParam Long targetUserId) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) return Result.error("未登录");
        chatService.clearUnread(userId, targetUserId);
        return Result.success(null);
    }

    @GetMapping("/unread")
    public Result<Integer> getUnread() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) return Result.error("未登录");
        int count = chatService.getTotalUnread(userId);
        return Result.success(count);
    }
}
