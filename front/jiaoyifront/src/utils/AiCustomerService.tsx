/**
 * AI 客服弹窗组件。
 * 复用 chatManager 的 WebSocket 通道，通过 type=ai-chat 与后端 AI 通信。
 */
import React, { useEffect, useRef, useState } from 'react';
import { Button, Input, Spin, message } from 'antd';
import { CloseOutlined, RobotOutlined, SendOutlined } from '@ant-design/icons';
import { chatManager } from './chatManager';

interface AiMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  pending?: boolean;
}

interface AiCustomerServiceProps {
  visible: boolean;
  onClose: () => void;
}

export const AiCustomerService: React.FC<AiCustomerServiceProps> = ({ visible, onClose }) => {
  const [messages, setMessages] = useState<AiMessage[]>([
    {
      id: 'welcome',
      role: 'assistant',
      content: '你好同学！我是交易行 AI 客服小交～\n平台规则、交易流程、信用分、违规申诉都能问我 😊',
    },
  ]);
  const [input, setInput] = useState('');
  const [streaming, setStreaming] = useState(false);
  const listRef = useRef<HTMLDivElement>(null);
  const aiBuffer = useRef<{ id: string; content: string } | null>(null);

  useEffect(() => {
    const unsubscribe = chatManager.subscribe((msg) => {
      if (msg.type !== 'ai-chat') return;
      // 后端 AI 通道发的是平铺字段：{type, role, content, done}，
      // 不是 {type, data:{...}}，所以从 msg 根上直接读。
      const m: any = msg;
      const role = m.role;
      const content = m.content ?? '';
      const done = m.done;

      if (role === 'user') {
        // 用户消息已经在本地加入了，这里忽略
        return;
      }

      if (role === 'assistant') {
        if (!aiBuffer.current) {
          const id = 'ai-' + Date.now();
          aiBuffer.current = { id, content: '' };
          setMessages((prev) => [
            ...prev,
            { id, role: 'assistant', content: '', pending: true },
          ]);
        }
        aiBuffer.current.content += content;
        const buffered = aiBuffer.current;
        setMessages((prev) =>
          prev.map((mm) =>
            mm.id === buffered.id
              ? { ...mm, content: buffered.content }
              : mm,
          ),
        );

        if (done) {
          setMessages((prev) =>
            prev.map((mm) =>
              mm.id === buffered.id
                ? { ...mm, pending: false }
                : mm,
            ),
          );
          aiBuffer.current = null;
          setStreaming(false);
        }
      }

      if (role === 'error') {
        message.error(content);
        aiBuffer.current = null;
        setStreaming(false);
      }
    });

    return unsubscribe;
  }, []);

  useEffect(() => {
    listRef.current?.scrollTo({
      top: listRef.current.scrollHeight,
      behavior: 'smooth',
    });
  }, [messages]);

  useEffect(() => {
    if (visible) {
      // 打开时自动滚动到底
      setTimeout(() => {
        listRef.current?.scrollTo({
          top: listRef.current.scrollHeight,
        });
      }, 50);
    }
  }, [visible]);

  const send = () => {
    const text = input.trim();
    if (!text || streaming) return;

    // 1. 本地加入用户消息
    setMessages((prev) => [
      ...prev,
      { id: 'u-' + Date.now(), role: 'user', content: text },
    ]);
    setInput('');
    setStreaming(true);

    // 2. 通过 WebSocket 发到后端
    chatManager.sendAiMessage(text);
  };

  const reset = () => {
    chatManager.sendAiReset();
    setMessages([
      {
        id: 'welcome',
        role: 'assistant',
        content: '已开启新一轮对话～',
      },
    ]);
  };

  if (!visible) return null;

  return (
    <div
      style={{
        position: 'fixed',
        right: 24,
        bottom: 150,
        width: 380,
        height: 540,
        background: '#fff',
        borderRadius: 12,
        boxShadow: '0 8px 32px rgba(0,0,0,0.18)',
        display: 'flex',
        flexDirection: 'column',
        zIndex: 9998,
        overflow: 'hidden',
      }}
    >
      {/* 顶部 */}
      <div
        style={{
          background: 'linear-gradient(135deg, #1890ff, #096dd9)',
          color: '#fff',
          padding: '14px 16px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <RobotOutlined style={{ fontSize: 20 }} />
          <div>
            <div style={{ fontWeight: 600 }}>AI 智能客服</div>
            <div style={{ fontSize: 12, opacity: 0.85 }}>基于平台规则，秒级回答</div>
          </div>
        </div>
        <Button
          type="text"
          icon={<CloseOutlined style={{ color: '#fff' }} />}
          onClick={onClose}
        />
      </div>

      {/* 消息列表 */}
      <div
        ref={listRef}
        style={{
          flex: 1,
          overflowY: 'auto',
          padding: 16,
          background: '#f7f8fa',
        }}
      >
        {messages.map((m) => (
          <div
            key={m.id}
            style={{
              display: 'flex',
              justifyContent: m.role === 'user' ? 'flex-end' : 'flex-start',
              marginBottom: 12,
            }}
          >
            <div
              style={{
                maxWidth: '78%',
                padding: '10px 14px',
                borderRadius: 10,
                background: m.role === 'user' ? '#1890ff' : '#fff',
                color: m.role === 'user' ? '#fff' : '#333',
                boxShadow:
                  m.role === 'user'
                    ? 'none'
                    : '0 1px 2px rgba(0,0,0,0.06)',
                whiteSpace: 'pre-wrap',
                wordBreak: 'break-word',
                lineHeight: 1.6,
                fontSize: 14,
              }}
            >
              {m.content}
              {m.pending && (
                <Spin size="small" style={{ marginLeft: 6 }} />
              )}
            </div>
          </div>
        ))}
      </div>

      {/* 底部输入区 */}
      <div
        style={{
          borderTop: '1px solid #eee',
          padding: 12,
          background: '#fff',
          display: 'flex',
          flexDirection: 'column',
          gap: 8,
        }}
      >
        <div style={{ display: 'flex', gap: 8 }}>
          <Input.TextArea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="想问点啥？试试：怎么发布考研资料？信用分被扣了怎么办？"
            autoSize={{ minRows: 1, maxRows: 3 }}
            onPressEnter={(e) => {
              if (!e.shiftKey) {
                e.preventDefault();
                send();
              }
            }}
            disabled={streaming}
          />
          <Button
            type="primary"
            icon={<SendOutlined />}
            onClick={send}
            disabled={streaming || !input.trim()}
          />
        </div>
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            fontSize: 12,
            color: '#999',
          }}
        >
          <span>Enter 发送 · Shift+Enter 换行</span>
          <Button type="link" size="small" onClick={reset}>
            清空对话
          </Button>
        </div>
      </div>
    </div>
  );
};

export default AiCustomerService;