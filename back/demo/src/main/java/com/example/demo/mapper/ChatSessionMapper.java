package com.example.demo.mapper;

import com.example.demo.entity.ChatSession;
import com.example.demo.dto.ChatSessionVO;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ChatSessionMapper {

    @Select("SELECT * FROM chat_session WHERE user_id = #{userId} AND target_user_id = #{targetUserId}")
    ChatSession findByUsers(@Param("userId") Long userId, @Param("targetUserId") Long targetUserId);

    @Insert("INSERT INTO chat_session (user_id, target_user_id, unread_count) VALUES (#{userId}, #{targetUserId}, 0)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ChatSession session);

    @Update("UPDATE chat_session SET unread_count = unread_count + 1, update_time = NOW() WHERE user_id = #{userId} AND target_user_id = #{targetUserId}")
    void incrementUnread(@Param("userId") Long userId, @Param("targetUserId") Long targetUserId);

    @Select("SELECT s.id, s.user_id, s.target_user_id, s.unread_count, s.create_time, " +
            "u.nickname as target_nickname, u.avatar as target_avatar, " +
            "m.content as last_message, m.create_time as last_message_time " +
            "FROM chat_session s " +
            "LEFT JOIN sys_user u ON u.id = s.target_user_id " +
            "LEFT JOIN chat_message m ON m.id = s.last_message_id " +
            "WHERE s.user_id = #{userId} ORDER BY s.update_time DESC")
    java.util.List<ChatSessionVO> findSessionsWithDetail(@Param("userId") Long userId);

    @Update("UPDATE chat_session SET last_message_id = #{messageId}, update_time = NOW() WHERE id = #{sessionId}")
    void updateLastMessage(@Param("sessionId") Long sessionId, @Param("messageId") Long messageId);

    @Update("UPDATE chat_session SET unread_count = 0, update_time = NOW() WHERE user_id = #{userId} AND target_user_id = #{targetUserId}")
    void clearUnread(@Param("userId") Long userId, @Param("targetUserId") Long targetUserId);
}
