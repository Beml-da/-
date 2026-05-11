// 运行时配置
import { RunTimeLayoutConfig } from '@umijs/max';
import { Avatar, Dropdown, Space, Typography, Badge, message } from 'antd';
import {
  BellOutlined,
  UserOutlined,
  LogoutOutlined,
  SettingOutlined,
} from '@ant-design/icons';
import { history } from '@umijs/max';
import type { MenuProps } from 'antd';
import { clearLoginInfo, getUserInfo } from './utils/useUser';
import { getPendingRequestCount } from './services/friend';
import { useEffect, useState, useRef } from 'react';
import { createPortal } from 'react-dom';
import { chatManager } from './utils/chatManager';

const { Text } = Typography;

// 自定义头像组件
const CustomAvatar: React.FC<{ currentUser: any }> = ({ currentUser }) => {
  const handleLogout = () => {
    clearLoginInfo();
    history.push('/login');
  };

  const menuItems: MenuProps['items'] = [
    {
      key: 'profile',
      icon: <UserOutlined />,
      label: '个人中心',
      onClick: () => history.push('/profile'),
    },
    {
      key: 'settings',
      icon: <SettingOutlined />,
      label: '账号设置',
    },
    { type: 'divider' },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
      danger: true,
      onClick: handleLogout,
    },
  ];

  return (
    <Dropdown menu={{ items: menuItems }} placement="bottomRight" trigger={['click']}>
      <Space style={{ cursor: 'pointer' }}>
        <Avatar
          size={36}
          src={currentUser?.avatar}
          icon={<UserOutlined />}
          style={{
            border: '2px solid #fff',
            boxShadow: '0 2px 8px rgba(0,0,0,0.15)',
          }}
        />
        <Text strong style={{ color: '#333' }}>
          {currentUser?.nickname || currentUser?.username || '未登录'}
        </Text>
      </Space>
    </Dropdown>
  );
};

// 悬浮球
const FloatingBall: React.FC<{ pendingCount: number }> = ({ pendingCount }) => {
  const [count, setCount] = useState(pendingCount);
  const [shaking, setShaking] = useState(false);
  const [mounted, setMounted] = useState(false);
  const [position, setPosition] = useState({ x: -1, y: -1 });
  const [isDragging, setIsDragging] = useState(false);
  const [isPressed, setIsPressed] = useState(false);
  const prevCount = useRef(pendingCount);
  const portalRef = useRef<HTMLDivElement | null>(null);
  const pressTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const pressStart = useRef({ x: 0, y: 0 });
  const dragOffset = useRef({ x: 0, y: 0 });
  const pressDownTime = useRef(0);

  useEffect(() => {
    portalRef.current = document.createElement('div');
    document.body.appendChild(portalRef.current);
    setMounted(true);
    chatManager.connect();
    return () => {
      if (portalRef.current) {
        document.body.removeChild(portalRef.current);
      }
    };
  }, []);

  useEffect(() => {
    const load = async () => {
      try {
        const res: any = await getPendingRequestCount();
        if (res.code === 200) {
          const n = res.data || 0;
          setCount(n);
          prevCount.current = n;
        }
      } catch {}
    };
    load();
    const timer = setInterval(load, 10000);
    return () => clearInterval(timer);
  }, []);

  useEffect(() => {
    if (count > prevCount.current) {
      setShaking(true);
      message.warning('您有新的好友申请');
      setTimeout(() => setShaking(false), 1000);
    }
    prevCount.current = count;
  }, [count]);

  const startPress = (e: React.MouseEvent | React.TouchEvent) => {
    const cx = 'touches' in e ? e.touches[0].clientX : e.clientX;
    const cy = 'touches' in e ? e.touches[0].clientY : e.clientY;
    pressStart.current = { x: cx, y: cy };
    pressDownTime.current = Date.now();
    pressTimer.current = setTimeout(() => {
      const curX = position.x < 0 ? window.innerWidth - 24 - 56 : position.x;
      const curY = position.y < 0 ? window.innerHeight - 80 - 56 : position.y;
      dragOffset.current = { x: cx - curX, y: cy - curY };
      setIsDragging(true);
      setIsPressed(false);
    }, 300);
    setIsPressed(true);
  };

  const move = (e: React.MouseEvent | React.TouchEvent) => {
    if (!isDragging) {
      const cx = 'touches' in e ? e.touches[0].clientX : e.clientX;
      const cy = 'touches' in e ? e.touches[0].clientY : e.clientY;
      const dx = Math.abs(cx - pressStart.current.x);
      const dy = Math.abs(cy - pressStart.current.y);
      if (dx > 5 || dy > 5) {
        if (pressTimer.current) clearTimeout(pressTimer.current);
        setIsPressed(false);
      }
      return;
    }
    const cx = 'touches' in e ? e.touches[0].clientX : e.clientX;
    const cy = 'touches' in e ? e.touches[0].clientY : e.clientY;
    const newX = cx - dragOffset.current.x;
    const newY = cy - dragOffset.current.y;
    const maxX = window.innerWidth - 56;
    const maxY = window.innerHeight - 56;
    setPosition({
      x: Math.max(0, Math.min(maxX, newX)),
      y: Math.max(0, Math.min(maxY, newY)),
    });
  };

  const endPress = (e?: React.MouseEvent | React.TouchEvent) => {
    if (pressTimer.current) {
      clearTimeout(pressTimer.current);
      pressTimer.current = null;
    }
    setIsPressed(false);
    setIsDragging(false);
    if (e) e.stopPropagation();
  };

  const handleClick = (e: React.MouseEvent) => {
    const elapsed = Date.now() - pressDownTime.current;
    if (elapsed >= 300) {
      e.stopPropagation();
      return;
    }
    history.push('/messages');
  };

  const ballStyle: React.CSSProperties = {
    position: 'fixed',
    left: position.x < 0 ? undefined : position.x,
    right: position.x < 0 ? '24px' : undefined,
    top: position.y < 0 ? undefined : position.y,
    bottom: position.y < 0 ? '80px' : undefined,
    width: 56,
    height: 56,
    background: count > 0
      ? 'linear-gradient(135deg, #ff4d4f, #cf1322)'
      : 'linear-gradient(135deg, #1890ff, #096dd9)',
    borderRadius: '50%',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    cursor: isDragging ? 'grabbing' : isPressed ? 'grab' : 'pointer',
    boxShadow: count > 0
      ? '0 4px 16px rgba(255, 77, 79, 0.5)'
      : '0 4px 16px rgba(24, 144, 255, 0.4)',
    zIndex: 9999,
    transform: isDragging ? 'scale(1.15)' : isPressed ? 'scale(1.08)' : 'scale(1)',
    transition: isDragging ? 'none' : 'transform 0.15s ease, box-shadow 0.3s, background 0.3s',
    animation: shaking ? 'shake 0.6s ease-in-out' : 'none',
    userSelect: 'none',
    touchAction: 'none',
  };

  const ball = (
    <>
      <style>{`
        @keyframes shake {
          0%, 100% { transform: translateX(0) rotate(0deg); }
          10% { transform: translateX(-6px) rotate(-8deg); }
          20% { transform: translateX(6px) rotate(8deg); }
          30% { transform: translateX(-6px) rotate(-8deg); }
          40% { transform: translateX(6px) rotate(8deg); }
          50% { transform: translateX(-4px) rotate(-5deg); }
          60% { transform: translateX(4px) rotate(5deg); }
          70% { transform: translateX(-3px) rotate(-3deg); }
          80% { transform: translateX(3px) rotate(3deg); }
          90% { transform: translateX(-1px) rotate(-1deg); }
        }
      `}</style>
      <div
        style={ballStyle}
        onMouseDown={startPress}
        onMouseMove={move}
        onMouseUp={(e) => endPress(e)}
        onMouseLeave={(e) => endPress(e)}
        onTouchStart={startPress}
        onTouchMove={move}
        onTouchEnd={(e) => endPress(e)}
        onClick={handleClick}
        title={count > 0 ? '您有新的好友申请' : '消息'}
      >
        <Badge count={count} size="small" offset={[6, -6]} overflowCount={99}>
          <BellOutlined style={{ fontSize: 22, color: '#fff' }} />
        </Badge>
      </div>
    </>
  );

  if (!mounted || !portalRef.current) return null;
  return createPortal(ball, portalRef.current);
};

export async function getInitialState(): Promise<{
  name: string;
  currentUser?: any;
  pendingCount?: number;
}> {
  const userStr = localStorage.getItem('userInfo');
  const currentUser = userStr ? JSON.parse(userStr) : null;

  let pendingCount = 0;
  try {
    const res: any = await getPendingRequestCount();
    if (res.code === 200) {
      pendingCount = res.data || 0;
    }
  } catch {}

  return {
    name: currentUser?.nickname ?? '的基地啊家的我',
    currentUser: currentUser,
    pendingCount: pendingCount,
  };
}

export const layout: RunTimeLayoutConfig = (props) => {
  return {
    logo: '/logo.png',
    title: '天天市场',
    layout: 'top',
    rightContentRender: () => (
      <>
        <FloatingBall pendingCount={props.initialState?.pendingCount ?? 0} />
        <CustomAvatar currentUser={props.initialState?.currentUser} />
      </>
    ),
  };
};
