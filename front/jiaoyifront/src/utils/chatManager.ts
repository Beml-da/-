/**
 * 聊天全局管理器（单例模式）
 * 管理 WebSocket 连接、全局未读数、消息广播
 */
import { getToken } from './useUser';

export interface WsMessage {
  type: string;
  data?: any;
}

type MessageHandler = (msg: WsMessage) => void;

class ChatManager {
  private ws: WebSocket | null = null;
  private subscribers: MessageHandler[] = [];
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private pingTimer: ReturnType<typeof setInterval> | null = null;
  private _connected = false;
  private _unreadCount = 0;
  private listeners: Set<() => void> = new Set();

  get connected() { return this._connected; }
  get unreadCount() { return this._unreadCount; }
  set unreadCount(v: number) {
    this._unreadCount = v;
    this.notify();
  }

  private get wsUrl() {
    // dev 直连后端，生产用同域
    const isDev = process.env.NODE_ENV === 'development';
    if (isDev) return 'ws://localhost:8080/ws/chat';
    const proto = window.location.protocol === 'https:' ? 'wss' : 'ws';
    return `${proto}://${window.location.host}/ws/chat`;
  }

  private notify() {
    this.listeners.forEach((l) => l());
  }

  connect() {
    const token = getToken();
    if (!token) {
      console.log('[Chat WS] 未登录，跳过连接');
      return;
    }
    if (this.ws?.readyState === WebSocket.OPEN) {
      console.log('[Chat WS] 已连接，跳过');
      return;
    }
    console.log('[Chat WS] 正在连接... token:', token.substring(0, 20) + '...');

    const ws = new WebSocket(`${this.wsUrl}?token=${token}`);
    this.ws = ws;

    ws.onopen = () => {
      console.log('[Chat WS] ✅ 已连接');
      this._connected = true;
      this.notify();
      this.pingTimer = setInterval(() => {
        if (ws.readyState === WebSocket.OPEN) {
          ws.send(JSON.stringify({ type: 'ping' }));
        }
      }, 30000);
    };

    ws.onmessage = (event) => {
      try {
        const msg: WsMessage = JSON.parse(event.data);
        console.log('[Chat WS] 收到消息:', msg);
        if (msg.type === 'pong') return;
        this.subscribers.forEach((h) => h(msg));
      } catch (e) {
        console.error('[Chat WS] 解析消息失败:', e);
      }
    };

    ws.onerror = () => {
      console.log('[Chat WS] ❌ 连接错误');
    };

    ws.onclose = () => {
      console.log('[Chat WS] 连接关闭');
      this._connected = false;
      this.notify();
      if (this.pingTimer) clearInterval(this.pingTimer);
      if (this.reconnectTimer) clearTimeout(this.reconnectTimer);
      this.reconnectTimer = setTimeout(() => this.connect(), 5000);
    };
  }

  disconnect() {
    if (this.reconnectTimer) clearTimeout(this.reconnectTimer);
    if (this.pingTimer) clearInterval(this.pingTimer);
    this.ws?.close();
    this.ws = null;
    this._connected = false;
    this.notify();
  }

  sendMessage(toId: number, content: string) {
    if (this.ws?.readyState === WebSocket.OPEN) {
      console.log('[Chat WS] 发送消息 toId:', toId, 'content:', content);
      this.ws.send(JSON.stringify({ type: 'chat', toId, content }));
    } else {
      console.log('[Chat WS] ❌ 发送失败，ws未连接 readyState:', this.ws?.readyState);
    }
  }

  subscribe(handler: MessageHandler) {
    this.subscribers.push(handler);
    return () => {
      this.subscribers = this.subscribers.filter((h) => h !== handler);
    };
  }

  onChange(listener: () => void) {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }
}

export const chatManager = new ChatManager();
