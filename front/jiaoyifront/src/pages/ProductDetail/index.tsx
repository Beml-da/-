import ProductImage from '@/components/ProductImage';
import { CONDITION_OPTIONS } from '@/constants/campus';
import { createOrder, getCurrentUser, getProduct } from '@/utils/api';
import {
  ClockCircleOutlined,
  EnvironmentOutlined,
  EyeOutlined,
  HeartOutlined,
  MessageOutlined,
  SafetyOutlined,
  ShareAltOutlined,
  ShopOutlined,
  StarOutlined,
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

const ProductDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [product, setProduct] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [selectedImage, setSelectedImage] = useState(0);
  const [isFavorite, setIsFavorite] = useState(false);
  const [messageVisible, setMessageVisible] = useState(false);
  const [messageContent, setMessageContent] = useState('');
  const [currentUser, setCurrentUser] = useState<any>(null);

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    Promise.all([getProduct(Number(id)), getCurrentUser()])
      .then(([productRes, userRes]: any[]) => {
        if (productRes.code === 200 && productRes.data) {
          const data = productRes.data;
          if (typeof data.tags === 'string') {
            try {
              data.tags = JSON.parse(data.tags);
            } catch {
              data.tags = [];
            }
          } else if (!Array.isArray(data.tags)) {
            data.tags = [];
          }
          setProduct(data);
        } else {
          setError('商品不存在或已下架');
        }
        if (userRes.code === 200 && userRes.data) {
          setCurrentUser(userRes.data);
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

  if (error || !product) {
    return (
      <div className={styles.container}>
        <div className={styles.notFound}>
          <h2>{error || '商品不存在或已下架'}</h2>
          <Button type="primary" onClick={() => navigate('/home')}>
            返回首页
          </Button>
        </div>
      </div>
    );
  }

  const conditionConfig = CONDITION_OPTIONS.find(
    (c) => c.value === product.condition,
  );
  const discount = product.originalPrice
    ? Math.round((1 - product.price / product.originalPrice) * 100)
    : 0;

  const parseJsonArray = (val: any): string[] => {
    if (!val) return [];
    if (Array.isArray(val)) return val;
    try {
      return JSON.parse(val);
    } catch {
      return [];
    }
  };

  const images: string[] = parseJsonArray(product.images);

  const handleBuy = async () => {
    try {
      const res = await createOrder({
        type: '商品',
        productId: Number(id),
      });
      if (res.code === 200) {
        message.success('订单已创建，请尽快支付');
        navigate('/orders');
      } else {
        message.error(res.message || '创建订单失败');
      }
    } catch {
      message.error('创建订单失败，请稍后重试');
    }
  };

  const handleContact = () => {
    message.success('正在打开聊天窗口...');
    navigate(`/messages?target=${product.sellerId}`);
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
        <Link
          to={`/categories?category=${encodeURIComponent(
            product.categoryName || '',
          )}`}
        >
          {product.categoryName || '商品'}
        </Link>
        <span>/</span>
        <span>商品详情</span>
      </div>

      <div className={styles.content}>
        {/* 左侧图片 */}
        <div className={styles.imageSection}>
          <div className={styles.mainImage}>
            <ProductImage
              src={images[selectedImage]}
              alt={product.title}
              style={{ width: '100%', height: '100%', position: 'absolute', top: 0, left: 0 }}
            />
            {discount > 0 && (
              <div className={styles.discountBadge}>-{discount}%</div>
            )}
          </div>
          {images.length > 1 && (
            <div className={styles.thumbnailList}>
              {images.map((img: string, index: number) => (
                <div
                  key={index}
                  className={`${styles.thumbnail} ${
                    index === selectedImage ? styles.active : ''
                  }`}
                  onClick={() => setSelectedImage(index)}
                >
                  <ProductImage src={img} alt={`图片${index + 1}`} />
                </div>
              ))}
            </div>
          )}
          {images.length === 0 && (
            <div className={styles.thumbnailList}>
              <div className={`${styles.thumbnail} ${styles.active}`}>
                <ProductImage
                  alt={product.title}
                  style={{
                    width: '100%',
                    height: '100%',
                    position: 'absolute',
                    top: 0,
                    left: 0,
                  }}
                />
              </div>
            </div>
          )}
        </div>

        {/* 右侧信息 */}
        <div className={styles.infoSection}>
          <div className={styles.header}>
            <h1 className={styles.title}>{product.title}</h1>
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
              <span className={styles.currentPrice}>¥{product.price}</span>
              {product.originalPrice && (
                <span className={styles.originalPrice}>
                  ¥{product.originalPrice}
                </span>
              )}
            </div>
            {product.isNegotiable === 1 && (
              <Tag color="orange" className={styles.negotiable}>
                可议价
              </Tag>
            )}
          </div>

          <div className={styles.meta}>
            {product.condition && (
              <Tag color={conditionConfig?.color}>{product.condition}</Tag>
            )}
            <span className={styles.metaItem}>
              <EyeOutlined /> {product.viewCount || 0} 次浏览
            </span>
            <span className={styles.metaItem}>
              <HeartOutlined /> {product.favoriteCount || 0} 次收藏
            </span>
          </div>

          <Divider />

          {/* 交易信息 */}
          <Descriptions column={1} size="small" className={styles.descriptions}>
            <Descriptions.Item label="交易地点">
              <EnvironmentOutlined /> {product.location || '未设置'}
            </Descriptions.Item>
            <Descriptions.Item label="发布时间">
              <ClockCircleOutlined />{' '}
              {product.createTime || product.createdAt || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="商品分类">
              {product.categoryName || product.category || '-'}
              {product.subCategory ? ` / ${product.subCategory}` : ''}
            </Descriptions.Item>
          </Descriptions>

          {/* 标签 */}
          {product.tags && product.tags.length > 0 && (
            <div className={styles.tags}>
              {product.tags.map((tag: string) => (
                <Tag key={tag} className={styles.tag}>
                  #{tag}
                </Tag>
              ))}
            </div>
          )}

          <Divider />

          {/* 卖家信息 */}
          {product.seller && (
            <div className={styles.sellerInfo}>
              <Avatar
                size={48}
                src={product.seller.avatar}
                icon={<ShopOutlined />}
              />
              <div className={styles.sellerDetail}>
                <div className={styles.sellerName}>
                  {product.seller.nickname || product.seller.username}
                  {product.seller.isVerified && (
                    <SafetyOutlined className={styles.verified} />
                  )}
                </div>
                <div className={styles.sellerMeta}>
                  <span className={styles.credit}>
                    <StarOutlined /> 信用 {product.seller.creditScore || 0}
                  </span>
                  <span className={styles.level}>
                    Lv.{product.seller.level || 1}
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
              联系卖家
            </Button>
            {(!currentUser || currentUser.id !== product.sellerId) && (
              <Button
                size="large"
                type="primary"
                icon={<ShopOutlined />}
                onClick={handleBuy}
                className={styles.buyBtn}
              >
                立即购买
              </Button>
            )}
          </div>

          {/* 快速留言 */}
          {messageVisible && (
            <div className={styles.quickMessage}>
              <TextArea
                placeholder="输入想对卖家说的话..."
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
              label: '商品描述',
              children: (
                <div className={styles.description}>
                  <p style={{ whiteSpace: 'pre-wrap' }}>
                    {product.description}
                  </p>
                  <div className={styles.notice}>
                    <h4>交易须知</h4>
                    <ul>
                      <li>请在校园内当面交易，注意人身安全</li>
                      <li>交易前仔细核对商品信息，有疑问可先联系卖家</li>
                      <li>完成交易后记得互相评价</li>
                      <li>如遇问题可在平台发起投诉</li>
                    </ul>
                  </div>
                </div>
              ),
            },
            {
              key: 'seller',
              label: '卖家信息',
              children: product.seller ? (
                <div className={styles.sellerProfile}>
                  <div className={styles.profileHeader}>
                    <Avatar size={64} src={product.seller.avatar} />
                    <div>
                      <h3>
                        {product.seller.nickname || product.seller.username}
                      </h3>
                      <p>@{product.seller.username}</p>
                    </div>
                  </div>
                  <Descriptions column={2}>
                    <Descriptions.Item label="信用评分">
                      {product.seller.creditScore || 0} 分
                    </Descriptions.Item>
                    <Descriptions.Item label="用户等级">
                      Lv.{product.seller.level || 1}
                    </Descriptions.Item>
                    <Descriptions.Item label="认证状态">
                      {product.seller.isVerified ? '已认证' : '未认证'}
                    </Descriptions.Item>
                    <Descriptions.Item label="注册时间">
                      {product.seller.createTime ||
                        product.seller.createdAt ||
                        '-'}
                    </Descriptions.Item>
                  </Descriptions>
                  <Button block size="large" className={styles.viewMoreBtn}>
                    查看更多商品
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

export default ProductDetailPage;
