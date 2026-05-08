import { getService } from '@/utils/api';
import {
  ClockCircleOutlined,
  CustomerServiceOutlined,
  EnvironmentOutlined,
  EyeOutlined,
  FileTextOutlined,
  HeartOutlined,
  MessageOutlined,
  SafetyOutlined,
  ShareAltOutlined,
  StarOutlined,
  TeamOutlined,
  ToolOutlined,
} from '@ant-design/icons';
import { Link, useNavigate, useParams } from '@umijs/max';
import {
  Avatar,
  Button,
  Card,
  Descriptions,
  Divider,
  Input,
  message,
  Spin,
  Tabs,
  Tag,
} from 'antd';
import { useEffect, useState } from 'react';
import styles from './index.less';

const { TextArea } = Input;

const ServiceDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [service, setService] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [isFavorite, setIsFavorite] = useState(false);
  const [messageVisible, setMessageVisible] = useState(false);
  const [messageContent, setMessageContent] = useState('');

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    getService(Number(id))
      .then((res: any) => {
        if (res.code === 200 && res.data) {
          const data = res.data;
          if (typeof data.tags === 'string') {
            try {
              data.tags = JSON.parse(data.tags);
            } catch {
              data.tags = [];
            }
          } else if (!Array.isArray(data.tags)) {
            data.tags = [];
          }
          setService(data);
        } else {
          setError('服务不存在或已下架');
        }
      })
      .catch(() => {
        setError('加载失败，请稍后重试');
      })
      .finally(() => {
        setLoading(false);
      });
  }, [id]);

  if (loading) {
    return (
      <div style={{ textAlign: 'center', marginTop: 100 }}>
        <Spin size="large" />
      </div>
    );
  }

  if (error || !service) {
    return (
      <div className={styles.container}>
        <div className={styles.notFound}>
          <h2>{error || '服务不存在或已下架'}</h2>
          <Button type="primary" onClick={() => navigate('/home')}>
            返回首页
          </Button>
        </div>
      </div>
    );
  }

  const serviceTypeConfig: Record<
    string,
    { icon: React.ReactNode; color: string }
  > = {
    学业辅导: { icon: <FileTextOutlined />, color: '#1677ff' },
    生活服务: { icon: <CustomerServiceOutlined />, color: '#52c41a' },
    技术帮助: { icon: <ToolOutlined />, color: '#722ed1' },
    其他: { icon: <TeamOutlined />, color: '#fa8c16' },
  };
  const typeInfo =
    serviceTypeConfig[service.serviceType] || serviceTypeConfig['其他'];

  const handleContact = () => {
    message.success('正在打开聊天窗口...');
    navigate(`/messages?target=${service.providerId}`);
  };

  const handleSendMessage = () => {
    if (messageContent.trim()) {
      message.success('消息已发送');
      setMessageContent('');
      setMessageVisible(false);
    }
  };

  return (
    <div className={styles.container}>
      {/* 面包屑 */}
      <div className={styles.breadcrumb}>
        <Link to="/">首页</Link>
        <span>/</span>
        <Link to="/categories">市场</Link>
        <span>/</span>
        <span>服务详情</span>
      </div>

      <div className={styles.content}>
        {/* 左侧服务图标区 */}
        <div className={styles.iconSection}>
          <div
            className={styles.serviceIconWrap}
            style={{
              background: `linear-gradient(135deg, ${typeInfo.color}22, ${typeInfo.color}44)`,
            }}
          >
            <div
              className={styles.serviceIcon}
              style={{ color: typeInfo.color }}
            >
              {typeInfo.icon}
            </div>
            <Tag color={typeInfo.color} className={styles.typeTag}>
              {service.serviceType}
            </Tag>
          </div>
        </div>

        {/* 右侧信息 */}
        <div className={styles.infoSection}>
          <div className={styles.header}>
            <h1 className={styles.title}>{service.title}</h1>
            <div className={styles.actions}>
              <Button
                icon={<HeartOutlined />}
                type={isFavorite ? 'primary' : 'default'}
                onClick={() => setIsFavorite(!isFavorite)}
                className={isFavorite ? styles.favorited : ''}
              >
                {isFavorite ? '已收藏' : '收藏'}
              </Button>
              <Button icon={<ShareAltOutlined />}>分享</Button>
            </div>
          </div>

          <div className={styles.priceSection}>
            <div className={styles.price}>
              <span className={styles.currentPrice}>¥{service.price}</span>
              {service.priceUnit && (
                <span className={styles.unit}>{service.priceUnit}</span>
              )}
            </div>
            {service.isNegotiable === 1 && (
              <Tag color="orange" className={styles.negotiable}>
                可议价
              </Tag>
            )}
          </div>

          <div className={styles.meta}>
            <Tag color={typeInfo.color}>{service.serviceType}</Tag>
            <span className={styles.metaItem}>
              <EyeOutlined /> {service.viewCount || 0} 次浏览
            </span>
            <span className={styles.metaItem}>
              <StarOutlined /> {service.rating || 0} 分
            </span>
            <span className={styles.metaItem}>
              {service.orderCount || 0} 次成交
            </span>
          </div>

          <Divider />

          {/* 服务信息 */}
          <Descriptions column={1} size="small" className={styles.descriptions}>
            <Descriptions.Item label="服务范围">
              <EnvironmentOutlined /> {service.location || '未设置'}
            </Descriptions.Item>
            <Descriptions.Item label="发布时间">
              <ClockCircleOutlined />{' '}
              {service.createTime || service.createdAt || '-'}
            </Descriptions.Item>
          </Descriptions>

          {/* 标签 */}
          {service.tags && service.tags.length > 0 && (
            <div className={styles.tags}>
              {service.tags.map((tag: string) => (
                <Tag key={tag} className={styles.tag}>
                  #{tag}
                </Tag>
              ))}
            </div>
          )}

          <Divider />

          {/* 服务者信息 */}
          {service.provider && (
            <div className={styles.sellerInfo}>
              <Avatar
                size={48}
                src={service.provider.avatar}
                icon={<TeamOutlined />}
              />
              <div className={styles.sellerDetail}>
                <div className={styles.sellerName}>
                  {service.provider.nickname || service.provider.username}
                  {service.provider.isVerified && (
                    <SafetyOutlined className={styles.verified} />
                  )}
                </div>
                <div className={styles.sellerMeta}>
                  <span className={styles.credit}>
                    <StarOutlined /> 信用 {service.provider.creditScore || 0}
                  </span>
                  <span className={styles.level}>
                    Lv.{service.provider.level || 1}
                  </span>
                </div>
              </div>
            </div>
          )}

          {/* 操作按钮 */}
          <div className={styles.actionButtons}>
            <Button
              size="large"
              icon={<MessageOutlined />}
              onClick={handleContact}
              className={styles.contactBtn}
            >
              联系服务者
            </Button>
            <Button
              size="large"
              type="primary"
              icon={<CustomerServiceOutlined />}
              onClick={() =>
                message.success('预约请求已发送，请等待服务者确认')
              }
              className={styles.buyBtn}
            >
              立即预约
            </Button>
          </div>

          {/* 快速留言 */}
          {messageVisible && (
            <div className={styles.quickMessage}>
              <TextArea
                placeholder="输入想对服务者说的话..."
                rows={3}
                value={messageContent}
                onChange={(e) => setMessageContent(e.target.value)}
              />
              <div className={styles.quickMessageActions}>
                <Button onClick={() => setMessageVisible(false)}>取消</Button>
                <Button type="primary" onClick={handleSendMessage}>
                  发送
                </Button>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* 详情标签页 */}
      <Card className={styles.detailCard}>
        <Tabs
          defaultActiveKey="description"
          items={[
            {
              key: 'description',
              label: '服务详情',
              children: (
                <div className={styles.description}>
                  <p style={{ whiteSpace: 'pre-wrap' }}>
                    {service.description}
                  </p>
                  <div className={styles.notice}>
                    <h4>服务须知</h4>
                    <ul>
                      <li>请提前与服务者沟通具体需求和时间</li>
                      <li>服务前确认好价格和交付标准</li>
                      <li>完成服务后记得互相评价</li>
                      <li>如遇问题可在平台发起投诉</li>
                    </ul>
                  </div>
                </div>
              ),
            },
            {
              key: 'provider',
              label: '服务者信息',
              children: service.provider ? (
                <div className={styles.sellerProfile}>
                  <div className={styles.profileHeader}>
                    <Avatar size={64} src={service.provider.avatar} />
                    <div>
                      <h3>
                        {service.provider.nickname || service.provider.username}
                      </h3>
                      <p>@{service.provider.username}</p>
                    </div>
                  </div>
                  <Descriptions column={2}>
                    <Descriptions.Item label="信用评分">
                      {service.provider.creditScore || 0} 分
                    </Descriptions.Item>
                    <Descriptions.Item label="用户等级">
                      Lv.{service.provider.level || 1}
                    </Descriptions.Item>
                    <Descriptions.Item label="认证状态">
                      {service.provider.isVerified ? '已认证' : '未认证'}
                    </Descriptions.Item>
                    <Descriptions.Item label="注册时间">
                      {service.provider.createTime ||
                        service.provider.createdAt ||
                        '-'}
                    </Descriptions.Item>
                  </Descriptions>
                  <Button block size="large" className={styles.viewMoreBtn}>
                    查看更多服务
                  </Button>
                </div>
              ) : null,
            },
          ]}
        />
      </Card>
    </div>
  );
};

export default ServiceDetailPage;
