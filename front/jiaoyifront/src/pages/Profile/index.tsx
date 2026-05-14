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
  IdcardOutlined,
  LockOutlined,
  LogoutOutlined,
  MailOutlined,
  PhoneOutlined,
  SafetyOutlined,
  SaveOutlined,
  SettingOutlined,
  ShopOutlined,
  StarOutlined,
  TrophyOutlined,
  UserOutlined,
  WalletOutlined,
} from '@ant-design/icons';
import { Link, useNavigate } from '@umijs/max';
import {
  Avatar,
  Button,
  Card,
  Divider,
  Form,
  Input,
  List,
  message,
  Progress,
  Tag,
} from 'antd';
import { useEffect, useState } from 'react';
import styles from './index.less';

const ProfilePage: React.FC = () => {
  const [form] = Form.useForm();
  const navigate = useNavigate();
  const [editing, setEditing] = useState(false);
  const [loading, setLoading] = useState(false);
  const [userInfo, setUserInfo] = useState<User | null>(null);
  const [stats, setStats] = useState({
    completedTrades: 0,
    inProgress: 0,
    favorites: 0,
    points: 0,
    creditScore: 0,
  });
  const [activities, setActivities] = useState<any[]>([]);

  useEffect(() => {
    loadUserData();
  }, []);

  const loadUserData = async () => {
    try {
      const res = await getCurrentUser();
      if (res.code === 200 && res.data) {
        setUserInfo(res.data);
        form.setFieldsValue({
          username: res.data.username,
          nickname: res.data.nickname,
          phone: res.data.phone,
          email: res.data.email,
          school: res.data.school,
          studentId: res.data.studentId,
        });
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
          : favRes.data.list || [];
        setStats((prev) => ({ ...prev, favorites: list.length }));
      }
    } catch (error) {
      console.error('加载用户数据失败', error);
    }
  };

  const handleEdit = () => setEditing(true);
  const handleCancel = () => {
    setEditing(false);
    if (userInfo) {
      form.setFieldsValue({
        username: userInfo.username,
        nickname: userInfo.nickname,
        phone: userInfo.phone,
        email: userInfo.email,
        school: userInfo.school,
        studentId: userInfo.studentId,
      });
    }
  };

  const handleSave = async () => {
    try {
      const values = await form.validateFields();
      setLoading(true);
      const res = await updateProfile(values);
      if (res.code === 200 && res.data) {
        setUserInfo(res.data);
        message.success('保存成功！');
      }
      setEditing(false);
    } catch (error) {
      console.error('保存失败:', error);
    } finally {
      setLoading(false);
    }
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
                />
                <div className={styles.userBadge}>
                  <TrophyOutlined /> Lv.{userInfo?.level || 1}
                </div>
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
                {editing ? (
                  <>
                    <Button icon={<CloseOutlined />} onClick={handleCancel}>
                      取消
                    </Button>
                    <Button
                      type="primary"
                      icon={<SaveOutlined />}
                      onClick={handleSave}
                      loading={loading}
                    >
                      保存
                    </Button>
                  </>
                ) : (
                  <Button
                    type="primary"
                    icon={<EditOutlined />}
                    onClick={handleEdit}
                  >
                    编辑资料
                  </Button>
                )}
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
        <div className={styles.bottomSection}>
          {/* 基本信息 */}
          <Card className={styles.infoCard} bordered={false}>
            <div className={styles.cardHeader}>
              <UserOutlined className={styles.cardIcon} />
              <span className={styles.cardTitle}>基本信息</span>
            </div>
            <Form form={form} layout="vertical" className={styles.form}>
              <Form.Item name="username" label="用户名">
                <Input prefix={<UserOutlined />} disabled />
              </Form.Item>
              <Form.Item name="nickname" label="昵称">
                <Input prefix={<UserOutlined />} disabled={!editing} />
              </Form.Item>
              <Form.Item name="phone" label="手机号">
                <Input prefix={<PhoneOutlined />} disabled={!editing} />
              </Form.Item>
              <Form.Item name="email" label="邮箱">
                <Input prefix={<MailOutlined />} disabled={!editing} />
              </Form.Item>
              <Form.Item name="school" label="学校">
                <Input prefix={<BankOutlined />} disabled={!editing} />
              </Form.Item>
              <Form.Item name="studentId" label="学号">
                <Input prefix={<IdcardOutlined />} disabled={!editing} />
              </Form.Item>
            </Form>
          </Card>

          {/* 账号安全 */}
          <Card className={styles.infoCard} bordered={false}>
            <div className={styles.cardHeader}>
              <SafetyOutlined className={styles.cardIcon} />
              <span className={styles.cardTitle}>账号安全</span>
            </div>
            <div className={styles.securityList}>
              <div className={styles.securityItem}>
                <div className={styles.securityLeft}>
                  <LockOutlined className={styles.securityIcon} />
                  <div>
                    <span className={styles.securityTitle}>登录密码</span>
                    <span className={styles.securityDesc}>已设置</span>
                  </div>
                </div>
                <Tag color="success">已设置</Tag>
              </div>
              <div className={styles.securityItem}>
                <div className={styles.securityLeft}>
                  <PhoneOutlined className={styles.securityIcon} />
                  <div>
                    <span className={styles.securityTitle}>绑定手机</span>
                    <span className={styles.securityDesc}>
                      {userInfo?.phone ? '已绑定' : '未绑定'}
                    </span>
                  </div>
                </div>
                <Tag color={userInfo?.phone ? 'success' : 'warning'}>
                  {userInfo?.phone ? '已绑定' : '未绑定'}
                </Tag>
              </div>
              <div className={styles.securityItem}>
                <div className={styles.securityLeft}>
                  <IdcardOutlined className={styles.securityIcon} />
                  <div>
                    <span className={styles.securityTitle}>校园认证</span>
                    <span className={styles.securityDesc}>
                      {userInfo?.isVerified ? '已认证' : '未认证'}
                    </span>
                  </div>
                </div>
                <Tag color={userInfo?.isVerified ? 'success' : 'warning'}>
                  {userInfo?.isVerified ? '已认证' : '未认证'}
                </Tag>
              </div>
            </div>
            <Divider />
            <Button type="text" danger className={styles.dangerBtn}>
              <LogoutOutlined /> 注销账号
            </Button>
          </Card>

          {/* 最近动态 */}
          <Card className={styles.infoCard} bordered={false}>
            <div className={styles.cardHeader}>
              <HistoryOutlined className={styles.cardIcon} />
              <span className={styles.cardTitle}>最近动态</span>
            </div>
            {activities.length === 0 ? (
              <div
                style={{
                  textAlign: 'center',
                  padding: '20px 0',
                  color: '#999',
                }}
              >
                暂无动态
              </div>
            ) : (
              <List
                className={styles.historyList}
                dataSource={activities}
                renderItem={(item: any) => (
                  <List.Item className={styles.historyItem}>
                    <item.icon className={styles.historyIcon} />
                    <div className={styles.historyContent}>
                      <span className={styles.historyAction}>
                        {item.action}
                      </span>
                      <span className={styles.historyTime}>{item.time}</span>
                    </div>
                  </List.Item>
                )}
              />
            )}
          </Card>
        </div>
      </div>
    </div>
  );
};

export default ProfilePage;
