import {
  BellOutlined,
  FieldStringOutlined,
  HomeOutlined,
  MessageOutlined,
  MoreOutlined,
  SearchOutlined,
  SendOutlined,
  ShoppingOutlined,
  SmileOutlined,
  StarOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { Avatar, Badge, Input } from 'antd';
import { useEffect, useRef, useState } from 'react';
import styles from './index.less';

const { Search } = Input;

const mockContacts = [
  { id: 1, name: '张三', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=Zhang', online: true },
  { id: 2, name: '李四', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=Li', online: true },
  { id: 3, name: '王五', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=Wang', online: false },
  { id: 4, name: '赵六', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=Zhao', online: true },
  { id: 5, name: '钱七', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=Qian', online: false },
  { id: 6, name: '孙八', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=Sun', online: true },
];

const mockSessions = [
  {
    id: 1,
    user: { id: 1, name: '张三', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=Zhang', online: true },
    lastMessage: '可以便宜点吗？',
    time: '12:30',
    unread: 2,
  },
  {
    id: 2,
    user: { id: 2, name: '李四', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=Li', online: true },
    lastMessage: '好的，明天见！',
    time: '昨天',
    unread: 0,
  },
  {
    id: 3,
    user: { id: 3, name: '王五', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=Wang', online: false },
    lastMessage: '收到，谢谢！',
    time: '昨天',
    unread: 0,
  },
  {
    id: 4,
    user: { id: 4, name: '赵六', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=Zhao', online: true },
    lastMessage: '这个商品还在吗？',
    time: '3天前',
    unread: 1,
  },
  {
    id: 5,
    user: { id: 5, name: '钱七', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=Qian', online: false },
    lastMessage: '价格可以商量',
    time: '上周',
    unread: 0,
  },
  {
    id: 6,
    user: { id: 6, name: '孙八', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=Sun', online: true },
    lastMessage: '好的，我考虑一下',
    time: '上周',
    unread: 0,
  },
];

const mockChatMessages = [
  { id: 1, fromId: 1, content: '你好，请问这个商品还在吗？', time: '12:20' },
  { id: 2, fromId: 'me', content: '在的，有什么问题吗？', time: '12:21' },
  { id: 3, fromId: 1, content: '价格可以便宜一点吗？', time: '12:25' },
  { id: 4, fromId: 'me', content: '已经是最低价了哦', time: '12:26' },
  { id: 5, fromId: 1, content: '那能包邮吗？', time: '12:28' },
  { id: 6, fromId: 'me', content: '江浙沪可以包邮，其他地区需要补差价', time: '12:29' },
  { id: 7, fromId: 1, content: '可以便宜点吗？', time: '12:30' },
];

const MessagesPage: React.FC = () => {
  const [activeTab, setActiveTab] = useState('全部');
  const [selectedSession, setSelectedSession] = useState<typeof mockSessions[0] | null>(null);
  const [inputMessage, setInputMessage] = useState('');
  const [messages, setMessages] = useState<typeof mockChatMessages>([]);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSelectSession = (session: typeof mockSessions[0]) => {
    setSelectedSession(session);
    setMessages(mockChatMessages);
  };

  const handleSendMessage = () => {
    if (!inputMessage.trim() || !selectedSession) return;
    const newMsg = {
      id: messages.length + 1,
      fromId: 'me',
      content: inputMessage,
      time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
    };
    setMessages([...messages, newMsg]);
    setInputMessage('');
  };

  const handleKeyPress = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      handleSendMessage();
    }
  };

  return (
    <div className={styles.container}>
      {/* ===== 左侧边栏 ===== */}
      <div className={styles.sidebar}>
        {/* 侧边栏头部 */}
        <div className={styles.sidebarHeader}>
          <span className={styles.sidebarTitle}>消息</span>
          <SearchOutlined className={styles.headerIcon} />
          <MoreOutlined className={styles.headerIcon} />
        </div>

        {/* 导航列表 */}
        <div className={styles.navList}>
          <div className={`${styles.navItem} ${styles.navItemActive}`}>
            <HomeOutlined className={styles.navIcon} />
            <span className={styles.navText}>首页</span>
          </div>
          <div className={styles.navItem}>
            <SearchOutlined className={styles.navIcon} />
            <span className={styles.navText}>搜索</span>
          </div>
          <div className={styles.navItem}>
            <MessageOutlined className={styles.navIcon} />
            <span className={styles.navText}>消息</span>
          </div>
          <div className={styles.navItem}>
            <StarOutlined className={styles.navIcon} />
            <span className={styles.navText}>收藏</span>
          </div>
          <div className={styles.navItem}>
            <ShoppingOutlined className={styles.navIcon} />
            <span className={styles.navText}>发布</span>
          </div>
          <div className={styles.navItem}>
            <UserOutlined className={styles.navIcon} />
            <span className={styles.navText}>我的</span>
          </div>
        </div>

        {/* 分隔线 */}
        <div className={styles.divider} />

        {/* 消息模块列表 */}
        <div className={styles.moduleList}>
          <div className={styles.moduleItem}>
            <BellOutlined className={styles.moduleIcon} />
            <span className={styles.moduleText}>消息通知</span>
          </div>
          <div className={styles.moduleItem}>
            <FieldStringOutlined className={styles.moduleIcon} />
            <span className={styles.moduleText}>分类栏</span>
          </div>
          <div className={styles.moduleItem}>
            <SmileOutlined className={styles.moduleIcon} />
            <span className={styles.moduleText}>互动回复</span>
          </div>
          <div className={styles.moduleItem}>
            <StarOutlined className={styles.moduleIcon} />
            <span className={styles.moduleText}>关注</span>
          </div>
        </div>

        {/* 分隔线 */}
        <div className={styles.divider} />

        {/* 联系人列表 */}
        <div className={styles.contactsSection}>
          <div className={styles.contactsHeader}>
            <span className={styles.contactsTitle}>联系人</span>
            <span className={styles.contactsAdd}>+</span>
          </div>
          <div className={styles.contactsList}>
            {mockContacts.map((contact) => (
              <div key={contact.id} className={styles.contactItem}>
                <div className={styles.contactAvatar}>
                  <Badge dot={contact.online} status="success" offset={[-2, 28]}>
                    <Avatar src={contact.avatar} size={38} />
                  </Badge>
                </div>
                <span className={styles.contactName}>{contact.name}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* ===== 中间消息列表 ===== */}
      <div className={styles.middlePanel}>
        {/* 搜索 */}
        <div className={styles.searchSection}>
          <Search
            placeholder="搜索"
            prefix={<SearchOutlined />}
            className={styles.searchInput}
          />
        </div>

        {/* 标签筛选 */}
        <div className={styles.tabSection}>
          {['全部', '商品', '服务', '系统'].map((tab) => (
            <span
              key={tab}
              className={`${styles.tabItem} ${activeTab === tab ? styles.tabActive : ''}`}
              onClick={() => setActiveTab(tab)}
            >
              {tab}
            </span>
          ))}
        </div>

        {/* 会话列表 */}
        <div className={styles.sessionList}>
          {mockSessions.map((session) => (
            <div
              key={session.id}
              className={`${styles.sessionItem} ${selectedSession?.id === session.id ? styles.sessionActive : ''}`}
              onClick={() => handleSelectSession(session)}
            >
              <div className={styles.sessionAvatarWrap}>
                <Badge dot={session.user.online} status="success" offset={[-4, 36]}>
                  <Avatar src={session.user.avatar} size={50} />
                </Badge>
                {session.unread > 0 && (
                  <span className={styles.unreadBadge}>{session.unread}</span>
                )}
              </div>
              <div className={styles.sessionInfo}>
                <div className={styles.sessionTop}>
                  <span className={styles.sessionName}>{session.user.name}</span>
                  <span className={styles.sessionTime}>{session.time}</span>
                </div>
                <span className={styles.sessionPreview}>{session.lastMessage}</span>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* ===== 右侧聊天窗口 ===== */}
      <div className={styles.chatPanel}>
        {selectedSession ? (
          <>
            {/* 聊天头部 */}
            <div className={styles.chatHeader}>
              <Badge dot={selectedSession.user.online} status="success" offset={[-4, 36]}>
                <Avatar src={selectedSession.user.avatar} size={44} />
              </Badge>
              <div className={styles.chatUserInfo}>
                <span className={styles.chatName}>{selectedSession.user.name}</span>
                <span className={styles.chatStatus}>
                  {selectedSession.user.online ? '在线' : '离线'}
                </span>
              </div>
            </div>

            {/* 消息列表 */}
            <div className={styles.chatMessages}>
              {messages.map((msg) => {
                const isMe = msg.fromId === 'me';
                return (
                  <div
                    key={msg.id}
                    className={`${styles.chatMsg} ${isMe ? styles.chatMsgMe : styles.chatMsgOther}`}
                  >
                    {!isMe && (
                      <Avatar src={selectedSession.user.avatar} size={36} className={styles.chatMsgAvatar} />
                    )}
                    <div className={`${styles.chatBubble} ${isMe ? styles.chatBubbleMe : styles.chatBubbleOther}`}>
                      {msg.content}
                    </div>
                    {isMe && (
                      <Avatar
                        src="https://api.dicebear.com/7.x/avataaars/svg?seed=Me"
                        size={36}
                        className={styles.chatMsgAvatar}
                      />
                    )}
                  </div>
                );
              })}
              <div ref={messagesEndRef} />
            </div>

            {/* 输入区域 */}
            <div className={styles.chatInputArea}>
              <div className={styles.inputToolbar}>
                <span className={styles.toolbarBtn}>图片</span>
                <span className={styles.toolbarBtn}>表情</span>
                <span className={styles.toolbarBtn}>商品</span>
              </div>
              <div className={styles.inputMain}>
                <Input
                  placeholder="输入信息..."
                  value={inputMessage}
                  onChange={(e) => setInputMessage(e.target.value)}
                  onPressEnter={handleKeyPress}
                  className={styles.chatInput}
                />
                <div className={styles.sendBtn} onClick={handleSendMessage}>
                  <SendOutlined />
                  <span className={styles.sendBtnText}>发送</span>
                </div>
              </div>
            </div>
          </>
        ) : (
          <div className={styles.chatEmpty}>
            <MessageOutlined className={styles.chatEmptyIcon} />
            <p>选择一个会话开始聊天</p>
          </div>
        )}
      </div>
    </div>
  );
};

export default MessagesPage;
