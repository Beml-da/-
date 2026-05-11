import * as Icons from '@ant-design/icons';
const {
  CameraOutlined,
  CloseOutlined,
  DownSquareOutlined,
  GiftOutlined,
  MessageOutlined,
  MoreOutlined,
  SearchOutlined,
  SendOutlined,
  SmileOutlined,
  UserAddOutlined,
} = Icons;
import { Avatar, Badge, Dropdown, Input, Modal, List, Button, Spin, message, Tabs } from 'antd';
import type { MenuProps } from 'antd';
import { useEffect, useRef, useState, useCallback } from 'react';
import {
  getMyFriends,
  searchUsers,
  addFriend,
  getPendingRequests,
  acceptFriendRequest,
  rejectFriendRequest,
  FriendVO,
  FriendRequestVO,
} from '@/services/friend';
import { getChatHistory, getChatSessions, markChatRead, getUnreadCount, ChatMessageVO, ChatSessionVO } from '@/services/chat';
import { chatManager } from '@/utils/chatManager';
import { getUserInfo } from '@/utils/useUser';
import styles from './index.less';

type Session = {
  id: number;
  user: { id: number; name: string; avatar?: string; online: boolean };
  lastMessage: string;
  time: string;
  unread: number;
};

type ChatMessage = {
  id: number;
  fromId: number | 'me';
  content: string;
  time: string;
};

const MessagesPage: React.FC = () => {
  const [selectedSession, setSelectedSession] = useState<Session | null>(null);
  const [inputMessage, setInputMessage] = useState('');
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [activeNavTab, setActiveNavTab] = useState<'message' | 'notice'>('message');
  const [friends, setFriends] = useState<FriendVO[]>([]);
  const [friendsLoading, setFriendsLoading] = useState(false);
  const [addFriendModalOpen, setAddFriendModalOpen] = useState(false);
  const [addFriendTab, setAddFriendTab] = useState<'search' | 'requests'>('search');

  // 搜索相关
  const [searchKeyword, setSearchKeyword] = useState('');
  const [searchLoading, setSearchLoading] = useState(false);
  const [searchResults, setSearchResults] = useState<FriendVO[]>([]);
  const [addedIds, setAddedIds] = useState<number[]>([]);
  const [addingId, setAddingId] = useState<number | null>(null);

  // 好友请求相关
  const [pendingRequests, setPendingRequests] = useState<FriendRequestVO[]>([]);
  const [requestsLoading, setRequestsLoading] = useState(false);
  const [handlingId, setHandlingId] = useState<number | null>(null);
  const [pendingCount, setPendingCount] = useState(0);

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const currentUserId = useRef<number>(0);
  const selectedSessionRef = useRef<Session | null>(null);

  useEffect(() => {
    const user = getUserInfo();
    if (user) currentUserId.current = user.id;
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  useEffect(() => {
    selectedSessionRef.current = selectedSession;
  }, [selectedSession]);

  useEffect(() => {
    chatManager.connect();
    loadFriends();
    loadPendingRequests();
    loadSessions();

    const unsub = chatManager.subscribe((msg) => {
      if (msg.type === 'chat' && msg.data) {
        const incoming: ChatMessageVO = msg.data;
        const session = selectedSessionRef.current;
        if (session && incoming.fromId === session.user.id) {
          const localMsg: ChatMessage = {
            id: incoming.id,
            fromId: incoming.fromId,
            content: incoming.content,
            time: formatTime(incoming.createTime),
          };
          setMessages((prev) => [...prev, localMsg]);
        }
        loadSessions();
      }
    });

    return () => {
      unsub();
    };
  }, []);

  const formatTime = (timeStr?: string) => {
    if (!timeStr) return '';
    const d = new Date(timeStr);
    return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
  };

  const loadFriends = async () => {
    setFriendsLoading(true);
    try {
      const res: any = await getMyFriends();
      if (res.code === 200) {
        setFriends(res.data || []);
      }
    } catch {
      message.error('加载好友列表失败');
    } finally {
      setFriendsLoading(false);
    }
  };

  const loadSessions = async () => {
    try {
      const res: any = await getChatSessions();
      if (res.code === 200) {
        const sessions: ChatSessionVO[] = res.data || [];
        setFriends(
          sessions.map((s) => ({
            id: s.targetUserId,
            nickname: s.targetNickname,
            avatar: s.targetAvatar,
            online: s.targetOnline === 1,
          })) as any
        );
      }
    } catch {}
  };

  const loadPendingRequests = async () => {
    setRequestsLoading(true);
    try {
      const res: any = await getPendingRequests();
      if (res.code === 200) {
        setPendingRequests(res.data || []);
        setPendingCount(res.data?.length || 0);
      }
    } catch {
      message.error('加载好友请求失败');
    } finally {
      setRequestsLoading(false);
    }
  };

  const handleSelectSession = (session: Session) => {
    setSelectedSession(session);
    setMessages([]);
    loadHistory(session.user.id);
    markChatRead(session.user.id);
  };

  const loadHistory = async (targetUserId: number) => {
    try {
      const res: any = await getChatHistory(targetUserId);
      if (res.code === 200) {
        const history: ChatMessageVO[] = res.data || [];
        const user = getUserInfo();
        const uid = user?.id || 0;
        setMessages(
          history.map((m: ChatMessageVO) => ({
            id: m.id,
            fromId: m.fromId === uid ? 'me' : m.fromId,
            content: m.content,
            time: formatTime(m.createTime),
          }))
        );
      }
    } catch {}
  };

  const handleSendMessage = useCallback(() => {
    if (!inputMessage.trim() || !selectedSession) return;
    const content = inputMessage.trim();
    const toId = selectedSession.user.id;
    console.log('[Messages] 发送消息 toId:', toId, 'content:', content);
    // 乐观更新
    const optimisticMsg: ChatMessage = {
      id: Date.now(),
      fromId: 'me',
      content,
      time: formatTime(new Date().toISOString()),
    };
    setMessages((prev) => [...prev, optimisticMsg]);
    setInputMessage('');
    // 通过 WebSocket 发送
    chatManager.sendMessage(toId, content);
  }, [inputMessage, selectedSession]);

  const handleKeyPress = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      handleSendMessage();
    }
  };

  const handleSearchFriend = async () => {
    if (!searchKeyword.trim()) return;
    setSearchLoading(true);
    try {
      const res: any = await searchUsers(searchKeyword.trim());
      if (res.code === 200) {
        setSearchResults(res.data || []);
      }
    } catch {
      message.error('搜索失败');
    } finally {
      setSearchLoading(false);
    }
  };

  const handleAddFriend = async (user: FriendVO) => {
    setAddingId(user.id);
    try {
      const res: any = await addFriend(user.id);
      if (res.code === 200) {
        setAddedIds((prev) => [...prev, user.id]);
        message.success(`已发送加好友请求给 ${user.nickname}`);
      } else {
        message.error(res.message || '发送失败');
      }
    } catch {
      message.error('发送失败');
    } finally {
      setAddingId(null);
    }
  };

  const handleAccept = async (requestId: number) => {
    setHandlingId(requestId);
    try {
      const res: any = await acceptFriendRequest(requestId);
      if (res.code === 200) {
        setPendingRequests((prev) => prev.filter((r) => r.id !== requestId));
        setPendingCount((prev) => Math.max(0, prev - 1));
        message.success('已同意好友请求');
        loadFriends();
      } else {
        message.error(res.message || '操作失败');
      }
    } catch {
      message.error('操作失败');
    } finally {
      setHandlingId(null);
    }
  };

  const handleReject = async (requestId: number) => {
    setHandlingId(requestId);
    try {
      const res: any = await rejectFriendRequest(requestId);
      if (res.code === 200) {
        setPendingRequests((prev) => prev.filter((r) => r.id !== requestId));
        setPendingCount((prev) => Math.max(0, prev - 1));
        message.success('已拒绝好友请求');
      } else {
        message.error(res.message || '操作失败');
      }
    } catch {
      message.error('操作失败');
    } finally {
      setHandlingId(null);
    }
  };

  const handleStartChat = (friend: FriendVO) => {
    const session: Session = {
      id: friend.id,
      user: { id: friend.id, name: friend.nickname, avatar: friend.avatar, online: friend.online },
      lastMessage: '',
      time: '刚刚',
      unread: 0,
    };
    setSelectedSession(session);
    setActiveNavTab('message');
  };

  const sessionMoreItems: MenuProps['items'] = [
    { key: 'top', label: '置顶' },
    { key: 'mute', label: '免打扰' },
    { key: 'delete', label: '删除会话', danger: true },
  ];

  const defaultAvatar = (seed: number) =>
    `https://api.dicebear.com/7.x/avataaars/svg?seed=${seed}`;

  // 打开弹窗时加载请求列表
  const handleModalOpen = (open: boolean) => {
    setAddFriendModalOpen(open);
    if (open) {
      setAddFriendTab('search');
      setSearchKeyword('');
      setSearchResults([]);
      setAddedIds([]);
      loadPendingRequests();
    }
  };

  const modalTabs = [
    {
      key: 'search',
      label: '添加好友',
      children: (
        <div>
          <div className={styles.modalSearchRow}>
            <Input
              placeholder="搜索用户名..."
              value={searchKeyword}
              onChange={(e) => setSearchKeyword(e.target.value)}
              onPressEnter={handleSearchFriend}
              className={styles.modalSearchInput}
              allowClear
            />
            <Button type="primary" onClick={handleSearchFriend} loading={searchLoading}>
              搜索
            </Button>
          </div>
          <Spin spinning={searchLoading}>
            <div className={styles.modalResultArea}>
              {searchResults.length > 0 ? (
                <List
                  dataSource={searchResults}
                  renderItem={(user) => (
                    <List.Item
                      key={user.id}
                      className={styles.modalUserItem}
                      actions={[
                        addedIds.includes(user.id) ? (
                          <span key="added" className={styles.addedTag}>已发送</span>
                        ) : (
                          <Button
                            key="add"
                            type="primary"
                            size="small"
                            loading={addingId === user.id}
                            onClick={() => handleAddFriend(user)}
                          >
                            加好友
                          </Button>
                        ),
                      ]}
                    >
                      <List.Item.Meta
                        avatar={
                          <Badge dot={user.online} status="success" offset={[-4, 30]}>
                            <Avatar src={user.avatar || defaultAvatar(user.id)} size={40} />
                          </Badge>
                        }
                        title={<span className={styles.modalUserName}>{user.nickname}</span>}
                        description={
                          <span className={styles.modalUserStatus}>
                            {user.school || '暂无学校'}
                          </span>
                        }
                      />
                    </List.Item>
                  )}
                />
              ) : searchKeyword && !searchLoading ? (
                <div className={styles.modalEmpty}>未找到相关用户</div>
              ) : (
                <div className={styles.modalEmpty}>输入用户名进行搜索</div>
              )}
            </div>
          </Spin>
        </div>
      ),
    },
    {
      key: 'requests',
      label: (
        <span>
          好友请求
          {pendingRequests.length > 0 && (
            <Badge count={pendingRequests.length} size="small" style={{ marginLeft: 6 }} />
          )}
        </span>
      ),
      children: (
        <Spin spinning={requestsLoading}>
          <div className={styles.modalResultArea}>
            {pendingRequests.length > 0 ? (
              <List
                dataSource={pendingRequests}
                renderItem={(req) => (
                  <List.Item
                    key={req.id}
                    className={styles.modalUserItem}
                    actions={[
                      <Button
                        key="accept"
                        type="primary"
                        size="small"
                        loading={handlingId === req.id}
                        onClick={() => handleAccept(req.id)}
                      >
                        同意
                      </Button>,
                      <Button
                        key="reject"
                        size="small"
                        loading={handlingId === req.id}
                        onClick={() => handleReject(req.id)}
                      >
                        拒绝
                      </Button>,
                    ]}
                  >
                    <List.Item.Meta
                      avatar={
                        <Avatar
                          src={req.fromAvatar || defaultAvatar(req.fromUserId)}
                          size={40}
                        />
                      }
                      title={<span className={styles.modalUserName}>{req.fromNickname}</span>}
                      description={
                        <span className={styles.modalUserStatus}>
                          {req.fromSchool || '暂无学校'}
                          {req.message ? ` · ${req.message}` : ''}
                        </span>
                      }
                    />
                  </List.Item>
                )}
              />
            ) : (
              <div className={styles.modalEmpty}>暂无好友请求</div>
            )}
          </div>
        </Spin>
      ),
    },
  ];

  return (
    <div className={styles.container}>
      {/* ===== 左侧边栏 ===== */}
      <div className={styles.sidebar}>
        <div className={styles.sidebarHeader}>
          <span className={styles.sidebarTitle}>好友</span>
          <div className={styles.sidebarHeaderRight}>
            <Badge count={pendingCount} size="small" offset={[4, -4]}>
              <UserAddOutlined
                className={styles.headerIcon}
                onClick={() => handleModalOpen(true)}
                title="加好友"
              />
            </Badge>
          </div>
        </div>

        <div className={styles.navList}>
          <div
            className={`${styles.navItem} ${activeNavTab === 'message' ? styles.navItemActive : ''}`}
            onClick={() => setActiveNavTab('message')}
          >
            <MessageOutlined className={styles.navIcon} />
            <span className={styles.navText}>消息</span>
          </div>
          <div
            className={`${styles.navItem} ${activeNavTab === 'notice' ? styles.navItemActive : ''}`}
            onClick={() => setActiveNavTab('notice')}
          >
            <GiftOutlined className={styles.navIcon} />
            <span className={styles.navText}>通知</span>
          </div>
        </div>

        <div className={styles.friendListSection}>
          <div className={styles.friendListTitle}>
            <span>好友列表</span>
            <span className={styles.friendCount}>{friends.length}</span>
          </div>
          <Spin spinning={friendsLoading}>
            <div className={styles.friendScroll}>
              {friends.length === 0 && !friendsLoading ? (
                <div className={styles.modalEmpty}>暂无好友，去添加一个吧</div>
              ) : (
                friends.map((friend) => (
                  <div
                    key={friend.id}
                    className={`${styles.friendItem} ${selectedSession?.user.id === friend.id ? styles.friendItemActive : ''}`}
                    onClick={() => handleStartChat(friend)}
                  >
                    <Badge dot={friend.online} status="success" offset={[-2, 30]}>
                      <Avatar src={friend.avatar || defaultAvatar(friend.id)} size={36} />
                    </Badge>
                    <span className={styles.friendName}>{friend.nickname}</span>
                    <div
                      className={styles.friendChatBtn}
                      onClick={(e) => {
                        e.stopPropagation();
                        handleStartChat(friend);
                      }}
                    >
                      <MessageOutlined />
                    </div>
                  </div>
                ))
              )}
            </div>
          </Spin>
        </div>
      </div>

      {/* ===== 中间消息列表 ===== */}
      <div className={styles.middlePanel}>
        <div className={styles.middleHeader}>
          <span className={styles.middleTitle}>
            {activeNavTab === 'message' ? '消息' : '通知'}
          </span>
        </div>

        <div className={styles.tabSection}>
          <Input
            placeholder="搜索聊天记录..."
            prefix={<SearchOutlined style={{ color: '#bfbfbf' }} />}
            className={styles.chatSearchInput}
          />
        </div>

        <div className={styles.sessionList}>
          {friends.length === 0 ? (
            <div className={styles.modalEmpty} style={{ padding: '40px 0' }}>
              还没有聊天记录
            </div>
          ) : (
            friends.map((friend) => (
              <div
                key={friend.id}
                className={`${styles.sessionItem} ${selectedSession?.id === friend.id ? styles.sessionActive : ''}`}
                onClick={() =>
                  handleSelectSession({
                    id: friend.id,
                    user: { id: friend.id, name: friend.nickname, avatar: friend.avatar, online: friend.online },
                    lastMessage: '',
                    time: '',
                    unread: 0,
                  })
                }
              >
                <div className={styles.sessionAvatarWrap}>
                  <Badge dot={friend.online} status="success" offset={[-4, 36]}>
                    <Avatar src={friend.avatar || defaultAvatar(friend.id)} size={48} />
                  </Badge>
                </div>
                <div className={styles.sessionInfo}>
                  <div className={styles.sessionTop}>
                    <span className={styles.sessionName}>{friend.nickname}</span>
                  </div>
                  <span className={styles.sessionPreview}>
                    {friend.school || '暂无学校信息'}
                  </span>
                </div>
                <Dropdown menu={{ items: sessionMoreItems }} trigger={['click']} placement="bottomRight">
                  <div className={styles.sessionMore} onClick={(e) => e.stopPropagation()}>
                    <MoreOutlined />
                  </div>
                </Dropdown>
              </div>
            ))
          )}
        </div>
      </div>

      {/* ===== 右侧聊天窗口 ===== */}
      <div className={styles.chatPanel}>
        {selectedSession ? (
          <>
            <div className={styles.chatHeader}>
              <Badge dot={selectedSession.user.online} status="success" offset={[-4, 36]}>
                <Avatar src={selectedSession.user.avatar || defaultAvatar(selectedSession.user.id)} size={44} />
              </Badge>
              <div className={styles.chatUserInfo}>
                <span className={styles.chatName}>{selectedSession.user.name}</span>
                <span className={`${styles.chatStatus} ${selectedSession.user.online ? styles.onlineStatus : styles.offlineStatus}`}>
                  {selectedSession.user.online ? '在线' : '离线'}
                </span>
              </div>
              <div className={styles.chatHeaderRight}>
                <MoreOutlined className={styles.chatHeaderIcon} />
                <CloseOutlined
                  className={styles.chatHeaderIcon}
                  onClick={() => setSelectedSession(null)}
                />
              </div>
            </div>

            <div className={styles.chatMessages}>
              {messages.map((msg) => {
                const isMe = msg.fromId === 'me';
                return (
                  <div
                    key={msg.id}
                    className={`${styles.chatMsg} ${isMe ? styles.chatMsgMe : styles.chatMsgOther}`}
                  >
                    {!isMe && (
                      <Avatar
                        src={selectedSession.user.avatar || defaultAvatar(selectedSession.user.id)}
                        size={36}
                        className={styles.chatMsgAvatar}
                      />
                    )}
                    <div className={`${styles.chatBubble} ${isMe ? styles.chatBubbleMe : styles.chatBubbleOther}`}>
                      {msg.content}
                    </div>
                    {isMe && (
                      <Avatar
                        src={defaultAvatar(0)}
                        size={36}
                        className={styles.chatMsgAvatar}
                      />
                    )}
                  </div>
                );
              })}
              <div ref={messagesEndRef} />
            </div>

            <div className={styles.chatInputArea}>
              <div className={styles.inputToolbar}>
                <CameraOutlined className={styles.toolbarBtn} />
                <GiftOutlined className={styles.toolbarBtn} />
                <SmileOutlined className={styles.toolbarBtn} />
                <DownSquareOutlined className={styles.toolbarBtn} />
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

      {/* ===== 加好友 + 好友请求弹窗 ===== */}
      <Modal
        title={
          <div className={styles.modalTitle}>
            <UserAddOutlined style={{ marginRight: 8, color: '#1890ff' }} />
            好友管理
          </div>
        }
        open={addFriendModalOpen}
        onCancel={() => setAddFriendModalOpen(false)}
        footer={null}
        width={480}
        destroyOnClose
        className={styles.addFriendModal}
      >
        <Tabs
          activeKey={addFriendTab}
          onChange={(key) => {
            setAddFriendTab(key as 'search' | 'requests');
            if (key === 'requests') {
              loadPendingRequests();
            }
          }}
          items={modalTabs}
        />
      </Modal>
    </div>
  );
};

export default MessagesPage;
