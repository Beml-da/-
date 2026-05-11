package com.example.demo.mapper;

import com.example.demo.entity.ChatMessage;
import com.example.demo.dto.ChatMessageVO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ChatMessageMapper {

    @Insert("INSERT INTO chat_message (session_id, type, from_id, to_id, content, is_read) " +
            "VALUES (#{sessionId}, #{type}, #{fromId}, #{toId}, #{content}, #{isRead})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ChatMessage message);

    @Select("SELECT m.*, u.nickname as from_nickname, u.avatar as from_avatar " +
            "FROM chat_message m " +
            "LEFT JOIN sys_user u ON u.id = m.from_id " +
            "WHERE (m.from_id = #{userId} AND m.to_id = #{targetUserId}) " +
            "   OR (m.from_id = #{targetUserId} AND m.to_id = #{userId}) " +
            "ORDER BY m.create_time ASC")
    List<ChatMessageVO> findHistory(@Param("userId") Long userId, @Param("targetUserId") Long targetUserId);

    @Update("UPDATE chat_message SET is_read = 1 WHERE to_id = #{userId} AND from_id = #{fromId} AND is_read = 0")
    void markRead(@Param("userId") Long userId, @Param("fromId") Long fromId);

    @Select("SELECT COUNT(*) FROM chat_message WHERE to_id = #{userId} AND is_read = 0")
    int countUnread(@Param("userId") Long userId);

    @Select("SELECT m.*, u.nickname as from_nickname, u.avatar as from_avatar " +
            "FROM chat_message m " +
            "LEFT JOIN sys_user u ON u.id = m.from_id " +
            "WHERE m.id = #{id}")
    ChatMessageVO findById(@Param("id") Long id);
}
