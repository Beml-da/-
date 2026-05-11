/**
 * 聊天服务 API
 */
import request from '@/utils/request';

export interface ChatMessageVO {
  id: number;
  sessionId: number;
  type: string;
  fromId: number;
  toId: number;
  content: string;
  isRead: number;
  createTime: string;
  fromNickname?: string;
  fromAvatar?: string;
}

export interface ChatSessionVO {
  id: number;
  userId: number;
  targetUserId: number;
  targetNickname: string;
  targetAvatar?: string;
  targetOnline: number;
  unreadCount: number;
  lastMessage?: string;
  lastMessageTime?: string;
  createTime: string;
}

export const getChatHistory = (targetUserId: number, limit = 50) => {
  return request.get<{ code: number; data: ChatMessageVO[] }>(
    `/api/chat/history?targetUserId=${targetUserId}&limit=${limit}`
  );
};

export const getChatSessions = () => {
  return request.get<{ code: number; data: ChatSessionVO[] }>('/api/chat/sessions');
};

export const markChatRead = (targetUserId: number) => {
  return request.post<{ code: number }>(`/api/chat/read?targetUserId=${targetUserId}`);
};

export const getUnreadCount = () => {
  return request.get<{ code: number; data: number }>('/api/chat/unread');
};
