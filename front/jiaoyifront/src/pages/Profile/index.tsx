import {
  getCurrentUser,
  getFavorites,
  getOrders,
  updateProfile,
  User,
} from '@/utils/api';
import {
  BankOutlined,
  CheckCircleOutlined,
  CloseOutlined,
  EditOutlined,
  HeartOutlined,
  HistoryOutlined,
  SaveOutlined,
  SettingOutlined,
  ShopOutlined,
  StarOutlined,
  UserOutlined,
  WalletOutlined,
} from '@ant-design/icons';
import { Link } from '@umijs/max';
import {
  Avatar,
  Button,
  Card,
  Divider,
  Form,
  Input,
  message,
  Modal,
  Progress,
  Tag,
} from 'antd';
import { useEffect, useState } from 'react';
import styles from './index.less';

const ProfilePage: React.FC = () => {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [userInfo, setUserInfo] = useState<User | null>(null);
  const [avatarModalVisible, setAvatarModalVisible] = useState(false);
  const [selectedAvatar, setSelectedAvatar] = useState<string>('');
  const [avatarSaving, setAvatarSaving] = useState(false);
  const [editModalVisible, setEditModalVisible] = useState(false);
  const [editing, setEditing] = useState(false);
  const [stats, setStats] = useState({
    completedTrades: 0,
    inProgress: 0,
    favorites: 0,
    points: 0,
    creditScore: 0,
  });

  useEffect(() => {
    loadUserData();
  }, []);

  const loadUserData = async () => {
    try {
      const res = await getCurrentUser();
      if (res.code === 200 && res.data) {
        setUserInfo(res.data);
      }

      const [orderRes, favRes] = await Promise.all([
        getOrders({ role: 'sell', pageSize: 100 }),
        getFavorites(),
      ]);

      if (orderRes.code === 200 && orderRes.data) {
        const list = Array.isArray(orderRes.data)
          ? orderRes.data
          : orderRes.data.list || [];
        setStats((prev) => ({
          ...prev,
          completedTrades: list.filter((o: any) => o.status === '已完成')
            .length,
          inProgress: list.filter((o: any) =>
            ['待付款', '待发货', '待收货'].includes(o.status),
          ).length,
        }));
      }
      if (favRes.code === 200 && favRes.data) {
        const list = Array.isArray(favRes.data)
          ? favRes.data
          : (favRes.data as any).list || [];
        setStats((prev) => ({ ...prev, favorites: list.length }));
      }
    } catch (error) {
      console.error('加载用户数据失败', error);
    }
  };

  const presetAvatars = [
    '/uploads/toonHead-1779154044779.png',
    '/uploads/toonHead-1779154062491.png',
    '/uploads/toonHead-1779154072151.png',
    '/uploads/toonHead-1779154079281.png',
    '/uploads/toonHead-1779154087476.png',
  ];

  const handleAvatarClick = () => {
    setSelectedAvatar(userInfo?.avatar || '');
    setAvatarModalVisible(true);
  };

  const handleAvatarSave = async () => {
    if (!selectedAvatar) {
      message.warning('请选择一张头像');
      return;
    }
    setAvatarSaving(true);
    try {
      const res = await updateProfile({ avatar: selectedAvatar });
      if (res.code === 200 && res.data) {
        setUserInfo(res.data);
        localStorage.setItem('userInfo', JSON.stringify(res.data));
        window.dispatchEvent(new CustomEvent('user-login-updated'));
        window.dispatchEvent(new CustomEvent('user-avatar-updated', { detail: res.data }));
        message.success('头像更换成功！');
        setAvatarModalVisible(false);
      } else {
        message.error(res.message || '更换头像失败');
      }
    } catch (error) {
      message.error('更换头像失败');
    } finally {
      setAvatarSaving(false);
    }
  };

  const handleEditClick = () => {
    if (userInfo) {
      form.setFieldsValue({
        nickname: userInfo.nickname,
        phone: userInfo.phone,
        email: userInfo.email,
        school: userInfo.school,
        studentId: userInfo.studentId,
      });
    }
    setEditModalVisible(true);
  };

  const handleEditSave = async () => {
    try {
      const values = await form.validateFields();
      setLoading(true);
      const res = await updateProfile(values);
      if (res.code === 200 && res.data) {
        setUserInfo(res.data);
        localStorage.setItem('userInfo', JSON.stringify(res.data));
        window.dispatchEvent(new CustomEvent('user-login-updated'));
        message.success('保存成功！');
        setEditModalVisible(false);
        setEditing(false);
      }
    } catch (error) {
      console.error('保存失败:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleEditCancel = () => {
    setEditModalVisible(false);
    setEditing(false);
  };

  const quickActions = [
    {
      icon: <ShopOutlined />,
      title: '我的订单',
      link: '/orders',
      color: '#667eea',
    },
    {
      icon: <HeartOutlined />,
      title: '我的收藏',
      link: '/favorites',
      color: '#eb2f96',
    },
    {
      icon: <HistoryOutlined />,
      title: '浏览记录',
      link: '/history',
      color: '#52c41a',
    },
    {
      icon: <SettingOutlined />,
      title: '账号设置',
      link: '/settings',
      color: '#fa8c16',
    },
  ];

  return (
    <div className={styles.container}>
      <div className={styles.mainContent}>
        {/* 顶部用户卡片 */}
        <div className={styles.topSection}>
          <Card className={styles.profileCard} bordered={false}>
            <div className={styles.profileTop}>
              <div className={styles.avatarSection}>
                <Avatar
                  size={80}
                  icon={<UserOutlined />}
                  src={userInfo?.avatar}
                  className={styles.avatar}
                  onClick={handleAvatarClick}
                  style={{ cursor: 'pointer' }}
                />
              </div>
              <div className={styles.userInfo}>
                <h2 className={styles.nickname}>
                  {userInfo?.nickname || userInfo?.username || '新用户'}
                </h2>
                <p className={styles.username}>@{userInfo?.username}</p>
                <div className={styles.schoolInfo}>
                  <BankOutlined /> {userInfo?.school || '未设置学校'}
                  {userInfo?.isVerified && (
                    <Tag color="success" className={styles.verifiedTag}>
                      <CheckCircleOutlined /> 已认证
                    </Tag>
                  )}
                </div>
              </div>
              <div className={styles.editBtnWrapper}>
                <Button
                  type="primary"
                  icon={<EditOutlined />}
                  onClick={handleEditClick}
                >
                  编辑资料
                </Button>
              </div>
            </div>

            <Divider className={styles.divider} />

            <div className={styles.statsSection}>
              <div className={styles.statItem}>
                <span className={styles.statValue}>
                  {stats.completedTrades}
                </span>
                <span className={styles.statLabel}>完成交易</span>
              </div>
              <div className={styles.statDivider} />
              <div className={styles.statItem}>
                <span className={styles.statValue}>{stats.inProgress}</span>
                <span className={styles.statLabel}>进行中</span>
              </div>
              <div className={styles.statDivider} />
              <div className={styles.statItem}>
                <span className={styles.statValue}>{stats.favorites}</span>
                <span className={styles.statLabel}>收藏</span>
              </div>
              <div className={styles.statDivider} />
              <div className={styles.statItem}>
                <span className={styles.statValue}>
                  <StarOutlined style={{ color: '#faad14' }} />{' '}
                  {userInfo?.creditScore || 0}
                </span>
                <span className={styles.statLabel}>信用分</span>
              </div>
              <div className={styles.statDivider} />
              <div className={styles.statItem}>
                <span className={styles.statValue}>
                  <WalletOutlined style={{ color: '#52c41a' }} />{' '}
                  {userInfo?.balance?.toFixed(2) || '0.00'}
                </span>
                <span className={styles.statLabel}>余额</span>
              </div>
            </div>

            <div className={styles.levelBar}>
              <span>成长值</span>
              <Progress
                percent={Math.min(
                  100,
                  (((userInfo?.level || 1) * 100) /
                    ((userInfo?.level || 1) * 500)) *
                    100,
                )}
                size="small"
                showInfo={false}
                strokeColor="#667eea"
              />
              <span>
                {stats.points}/{(userInfo?.level || 1) * 500}
              </span>
            </div>
          </Card>
        </div>

        {/* 快捷入口 */}
        <div className={styles.quickActions}>
          {quickActions.map((action, index) => (
            <Link
              key={index}
              to={action.link}
              className={styles.quickActionCard}
            >
              <div
                className={styles.quickActionIcon}
                style={{ background: action.color }}
              >
                {action.icon}
              </div>
              <span className={styles.quickActionTitle}>{action.title}</span>
            </Link>
          ))}
        </div>

        {/* 下方卡片区域 */}
        <div className={styles.bottomSection} />
      </div>

      {/* 头像选择模态框 */}
      <Modal
        title="选择头像"
        open={avatarModalVisible}
        onCancel={() => setAvatarModalVisible(false)}
        footer={[
          <Button key="cancel" onClick={() => setAvatarModalVisible(false)}>
            取消
          </Button>,
          <Button
            key="confirm"
            type="primary"
            loading={avatarSaving}
            onClick={handleAvatarSave}
            disabled={!selectedAvatar}
          >
            确认更换
          </Button>,
        ]}
        centered
        width={480}
      >
        <div style={{ padding: '8px 0' }}>
          <p style={{ marginBottom: 16, color: '#666', fontSize: 13 }}>
            选择一张喜欢的头像，点击确认更换
          </p>
          <div className={styles.avatarGrid}>
            {presetAvatars.map((url, index) => (
              <div
                key={index}
                className={styles.avatarOption}
                style={{
                  border: selectedAvatar === url
                    ? '3px solid #667eea'
                    : '3px solid transparent',
                  boxShadow: selectedAvatar === url
                    ? '0 0 0 3px rgba(102, 126, 234, 0.2)'
                    : 'none',
                }}
                onClick={() => setSelectedAvatar(url)}
              >
                <img
                  src={url}
                  alt={`头像${index + 1}`}
                  style={{ width: '100%', height: '100%', objectFit: 'cover', borderRadius: 8 }}
                />
                {selectedAvatar === url && (
                  <div className={styles.checkOverlay}>
                    <span style={{ color: '#fff', fontSize: 20 }}>✓</span>
                  </div>
                )}
              </div>
            ))}
          </div>
          {selectedAvatar && (
            <div style={{ marginTop: 16, textAlign: 'center' }}>
              <span style={{ fontSize: 13, color: '#999' }}>当前选择：</span>
              <Avatar
                src={selectedAvatar}
                size={56}
                style={{ marginLeft: 8, verticalAlign: 'middle' }}
              />
            </div>
          )}
        </div>
      </Modal>

      {/* 编辑资料模态框 */}
      <Modal
        title="编辑资料"
        open={editModalVisible}
        onCancel={handleEditCancel}
        footer={[
          <Button key="cancel" icon={<CloseOutlined />} onClick={handleEditCancel}>
            取消
          </Button>,
          <Button
            key="confirm"
            type="primary"
            icon={<SaveOutlined />}
            loading={loading}
            onClick={handleEditSave}
          >
            保存
          </Button>,
        ]}
        centered
        width={480}
      >
        <Form
          form={form}
          layout="vertical"
          style={{ marginTop: 16 }}
        >
          <Form.Item
            name="nickname"
            label="昵称"
            rules={[{ required: true, message: '请输入昵称' }]}
          >
            <Input prefix={<EditOutlined />} placeholder="请输入昵称" />
          </Form.Item>
          <Form.Item
            name="phone"
            label="手机号"
          >
            <Input prefix={<WalletOutlined />} placeholder="请输入手机号" />
          </Form.Item>
          <Form.Item
            name="email"
            label="邮箱"
          >
            <Input prefix={<StarOutlined />} placeholder="请输入邮箱" />
          </Form.Item>
          <Form.Item
            name="school"
            label="学校"
          >
            <Input prefix={<BankOutlined />} placeholder="请输入学校" />
          </Form.Item>
          <Form.Item
            name="studentId"
            label="学号"
          >
            <Input prefix={<UserOutlined />} placeholder="请输入学号" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default ProfilePage;
