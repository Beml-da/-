// 运行时配置
import { RuntimeConfig } from '@umijs/max';
import { Avatar, Dropdown, Space, Typography } from 'antd';
import {
  UserOutlined,
  LogoutOutlined,
  SettingOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import { history } from '@umijs/max';
import type { MenuProps } from 'antd';
import { clearLoginInfo } from './utils/useUser';

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

// 全局初始化数据配置
export async function getInitialState(): Promise<{
  name: string;
  currentUser?: any;
}> {
  // 从 localStorage 读取用户信息
  const userStr = localStorage.getItem('userInfo');
  const currentUser = userStr ? JSON.parse(userStr) : null;

  return {
    name: currentUser?.nickname ?? '天天市场',
    currentUser: currentUser,
  };
}

export const layout: RuntimeConfig['layout'] = (props) => {
  return {
    logo: '/logo.png',
    title: '天天市场',
    layout: 'top',
    rightContentRender: () => (
      <CustomAvatar currentUser={props.initialState?.currentUser} />
    ),
  };
};
