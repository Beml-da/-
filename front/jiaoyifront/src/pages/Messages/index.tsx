import * as Icons from '@ant-design/icons';
const {
  CameraOutlined,
  CloseOutlined,
  DeleteOutlined,
  DownSquareOutlined,
  GiftOutlined,
  MessageOutlined,
  MoreOutlined,
  SendOutlined,
  SmileOutlined,
  UserAddOutlined,
} = Icons;
import { Avatar, Badge, Dropdown, Input, Modal, List, Button, Spin, message, Tabs } from 'antd';
import { useEffect, useRef, useState, useCallback } from 'react';
import {
  getMyFriends,
  searchUsers,
  addFriend,
  getPendingRequests,
  acceptFriendRequest,
  rejectFriendRequest,
  deleteFriend,
  FriendVO,
  FriendRequestVO,
} from '@/services/friend';
import { getChatHistory, markChatRead, ChatMessageVO } from '@/services/chat';
import { chatManager } from '@/utils/chatManager';
import { getUserInfo } from '@/utils/useUser';
import styles from './index.less';

type Session = {
  id: number;
  user: { id: number; name: string; avatar?: string; online: boolean };
};

type ChatMessage = {
  id: number;
  fromId: number | 'me';
  content: string;
  time: string;
  createTime?: string;
  status?: 'sending' | 'sent' | 'failed';
};

const FriendItem: React.FC<{
  friend: FriendVO;
  active: boolean;
  onStartChat: (friend: FriendVO) => void;
  onClickItem: (friend: FriendVO) => void;
  defaultAvatar: (seed: number) => string;
}> = ({ friend, active, onStartChat, onClickItem, defaultAvatar }) => {
  return (
    <div
      className={`${styles.friendItem} ${active ? styles.friendItemActive : ''}`}
      onClick={() => onClickItem(friend)}
    >
      <Badge dot={friend.online} status="success" offset={[-2, 30]}>
        <Avatar src={friend.avatar || defaultAvatar(friend.id)} size={36} />
      </Badge>
      <span className={styles.friendName}>{friend.nickname}</span>
      <div
        className={styles.friendChatBtn}
        onClick={(e) => {
          e.stopPropagation();
          onStartChat(friend);
        }}
      >
        <MessageOutlined />
      </div>
    </div>
  );
};

const MessagesPage: React.FC = () => {
  const [selectedSession, setSelectedSession] = useState<Session | null>(null);
  const [inputMessage, setInputMessage] = useState('');
  const [messages, setMessages] = useState<ChatMessage[]>([]);
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

    const unsub = chatManager.subscribe((msg) => {
      if (msg.type === 'chat' && msg.data) {
        const incoming: ChatMessageVO = msg.data;
        const session = selectedSessionRef.current;
        if (!session) return;
        const isCurrentSession =
          (incoming.fromId === session.user.id) || (incoming.toId === session.user.id);
        if (!isCurrentSession) return;

        const me = currentUserId.current;
        if (incoming.fromId === me) {
          // 自己发出去的消息回执（sent 或 failed）
          if (incoming.status === 'failed') {
            setMessages((prev) => [
              ...prev.filter((m) => !(m.fromId === 'me' && m.content === incoming.content && m.status === 'sending')),
              {
                id: incoming.id,
                fromId: 'me',
                content: incoming.content,
                time: formatTime(incoming.createTime),
                createTime: incoming.createTime,
                status: 'failed',
              },
            ]);
          } else {
            setMessages((prev) =>
              prev.map((m) =>
                m.fromId === 'me' && m.content === incoming.content && m.status === 'sending'
                  ? { ...m, id: incoming.id, status: 'sent' }
                  : m
              )
            );
          }
        } else {
          // 对方发来的消息
          setMessages((prev) => [
            ...prev,
            {
              id: incoming.id,
              fromId: incoming.fromId,
              content: incoming.content,
              time: formatTime(incoming.createTime),
              createTime: incoming.createTime,
              status: 'sent',
            },
          ]);
        }
      } else if (msg.type === 'friend-removed' && msg.data) {
        const removedBy = msg.data.removedBy as number;
        const session = selectedSessionRef.current;
        if (session && session.user.id === removedBy) {
          message.warning(`${session.user.name} 已将你删除为好友`);
        }
        loadFriends();
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

  const formatFullTime = (timeStr?: string) => {
    if (!timeStr) return '';
    const d = new Date(timeStr);
    const pad = (n: number) => n.toString().padStart(2, '0');
    return `${d.getFullYear()}/${pad(d.getMonth() + 1)}/${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
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
            createTime: m.createTime,
            status: m.fromId === uid ? 'sent' : undefined,
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
    const now = new Date().toISOString();
    const optimisticMsg: ChatMessage = {
      id: Date.now(),
      fromId: 'me',
      content,
      time: formatTime(now),
      createTime: now,
      status: 'sending',
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
        window.dispatchEvent(new Event('friend-requests-updated'));
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
        window.dispatchEvent(new Event('friend-requests-updated'));
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
    };
    setSelectedSession(session);
    setMessages([]);
    loadHistory(friend.id);
    markChatRead(friend.id);
  };

  const handleDeleteFriend = (target: FriendVO | Session['user']) => {
    const name = (target as FriendVO).nickname || (target as Session['user']).name;
    Modal.confirm({
      title: '删除好友',
      content: `确定要删除好友 "${name}" 吗？删除后将无法继续给对方发送消息。`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          const res: any = await deleteFriend(target.id);
          if (res.code === 200) {
            message.success('已删除好友');
            setFriends((prev) => prev.filter((f) => f.id !== target.id));
            if (selectedSession?.user.id === target.id) {
              setSelectedSession(null);
              setMessages([]);
            }
          } else {
            message.error(res.message || '删除失败');
          }
        } catch {
          message.error('删除失败');
        }
      },
    });
  };

  const defaultAvatar = (seed: number) =>
    `https://api.dicebear.com/7.x/avataaars/svg?seed=${seed}`;

  const [currentUserAvatar, setCurrentUserAvatar] = useState(() => {
    const user = getUserInfo();
    return user?.avatar || defaultAvatar(user?.id || 0);
  });

  useEffect(() => {
    const handleLoginUpdate = () => {
      const user = getUserInfo();
      setCurrentUserAvatar(user?.avatar || defaultAvatar(user?.id || 0));
    };
    window.addEventListener('user-login-updated', handleLoginUpdate);
    return () => window.removeEventListener('user-login-updated', handleLoginUpdate);
  }, []);

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
                  <FriendItem
                    key={friend.id}
                    friend={friend}
                    active={selectedSession?.user.id === friend.id}
                    onStartChat={handleStartChat}
                    onClickItem={handleStartChat}
                    defaultAvatar={defaultAvatar}
                  />
                ))
              )}
            </div>
          </Spin>
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
                <Dropdown
                  trigger={['click']}
                  placement="bottomRight"
                  menu={{
                    items: [
                      {
                        key: 'delete',
                        label: '删除好友',
                        icon: <DeleteOutlined />,
                        danger: true,
                        onClick: () => handleDeleteFriend(selectedSession.user),
                      },
                    ],
                  }}
                >
                  <MoreOutlined className={styles.chatHeaderIcon} />
                </Dropdown>
                <CloseOutlined
                  className={styles.chatHeaderIcon}
                  onClick={() => setSelectedSession(null)}
                />
              </div>
            </div>

            <div className={styles.chatMessages}>
              {messages.map((msg) => {
                const isMe = msg.fromId === 'me';
                const isFailed = msg.status === 'failed';
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
                    {isMe && (
                      <div className={styles.chatMsgTime}>
                        {formatFullTime(msg.createTime)}
                      </div>
                    )}
                    {isMe && isFailed && (
                      <span className={styles.chatMsgError} title="消息发送失败，对方已不是你的好友">
                        <CloseOutlined />
                      </span>
                    )}
                    <div className={`${styles.chatBubble} ${isMe ? styles.chatBubbleMe : styles.chatBubbleOther} ${isFailed ? styles.chatBubbleFailed : ''}`}>
                      {msg.content}
                    </div>
                    {!isMe && (
                      <div className={styles.chatMsgTime}>
                        {formatFullTime(msg.createTime)}
                      </div>
                    )}
                    {isMe && !isFailed && (
                      <Avatar
                        src={currentUserAvatar}
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
