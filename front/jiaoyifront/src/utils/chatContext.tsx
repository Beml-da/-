/**
 * 聊天全局上下文
 * - 管理 WebSocket 连接
 * - 提供全局未读数
 * - 广播消息给订阅者
 */
import { createContext, useContext, useEffect, useRef, useState, useCallback } from 'react';
import { getToken } from './useUser';
import { message } from 'antd';

interface WsMessage {
  type: string;
  data?: any;
}

type MessageHandler = (msg: WsMessage) => void;

interface ChatContextValue {
  unreadCount: number;
  setUnreadCount: (n: number) => void;
  sendMessage: (toId: number, content: string) => void;
  subscribe: (handler: MessageHandler) => () => void;
  connect: () => void;
  disconnect: () => void;
  connected: boolean;
}

const ChatContext = createContext<ChatContextValue>({
  unreadCount: 0,
  setUnreadCount: () => {},
  sendMessage: () => {},
  subscribe: () => () => {},
  connect: () => {},
  disconnect: () => {},
  connected: false,
});

export const useChat = () => useContext(ChatContext);

const WS_URL = `${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.host}/ws/chat`;

export const ChatProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [unreadCount, setUnreadCount] = useState(0);
  const [connected, setConnected] = useState(false);
  const wsRef = useRef<WebSocket | null>(null);
  const subscribersRef = useRef<MessageHandler[]>([]);
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const pingTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const connect = useCallback(() => {
    const token = getToken();
    if (!token || wsRef.current?.readyState === WebSocket.OPEN) return;

    const ws = new WebSocket(`${WS_URL}?token=${token}`);
    wsRef.current = ws;

    ws.onopen = () => {
      console.log('[Chat WS] 已连接');
      setConnected(true);
      // 心跳
      pingTimerRef.current = setInterval(() => {
        if (ws.readyState === WebSocket.OPEN) {
          ws.send(JSON.stringify({ type: 'ping' }));
        }
      }, 30000);
    };

    ws.onmessage = (event) => {
      try {
        const msg: WsMessage = JSON.parse(event.data);
        if (msg.type === 'pong') return;
        // 广播给所有订阅者
        subscribersRef.current.forEach((h) => h(msg));
      } catch {}
    };

    ws.onerror = () => {
      console.log('[Chat WS] 连接错误');
    };

    ws.onclose = () => {
      console.log('[Chat WS] 连接关闭');
      setConnected(false);
      if (pingTimerRef.current) clearInterval(pingTimerRef.current);
      // 自动重连
      if (reconnectTimerRef.current) clearTimeout(reconnectTimerRef.current);
      reconnectTimerRef.current = setTimeout(connect, 5000);
    };
  }, []);

  const disconnect = useCallback(() => {
    if (reconnectTimerRef.current) clearTimeout(reconnectTimerRef.current);
    if (pingTimerRef.current) clearInterval(pingTimerRef.current);
    wsRef.current?.close();
    wsRef.current = null;
    setConnected(false);
  }, []);

  const sendMessage = useCallback((toId: number, content: string) => {
    const ws = wsRef.current;
    if (ws?.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({ type: 'chat', toId, content }));
    }
  }, []);

  const subscribe = useCallback((handler: MessageHandler) => {
    subscribersRef.current.push(handler);
    return () => {
      subscribersRef.current = subscribersRef.current.filter((h) => h !== handler);
    };
  }, []);

  // 组件挂载时自动连接
  useEffect(() => {
    connect();
    return () => disconnect();
  }, [connect, disconnect]);

  return (
    <ChatContext.Provider
      value={{ unreadCount, setUnreadCount, sendMessage, subscribe, connect, disconnect, connected }}
    >
      {children}
    </ChatContext.Provider>
  );
};
