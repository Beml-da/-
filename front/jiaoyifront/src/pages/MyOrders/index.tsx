import ProductImage from '@/components/ProductImage';
import { ORDER_STATUS_CONFIG } from '@/constants/campus';
import {
  approveRefund,
  applyRefund,
  cancelOrder as cancelOrderApi,
  confirmReceiveOrder,
  getLatestRefund,
  getMyProducts,
  getMyServices,
  getOrders,
  getProduct,
  payOrder,
  rejectRefund,
  shipOrder,
} from '@/utils/api';
import { getUserInfo, refreshUserInfo } from '@/utils/useUser';
import {
  ClockCircleOutlined,
  ShopOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { Link, useNavigate } from '@umijs/max';
import {
  Alert,
  Avatar,
  Button,
  Card,
  Divider,
  Empty,
  Input,
  message,
  Modal,
  Rate,
  Tabs,
  Tag,
} from 'antd';
import { useCallback, useEffect, useState } from 'react';
import styles from './index.less';

const { TextArea } = Input;

interface OrderExt {
  id: number;
  orderNo: string;
  type: '商品' | '服务';
  productId?: number;
  serviceId?: number;
  product?: any;
  service?: any;
  buyerId: number;
  sellerId: number;
  buyer?: any;
  seller?: any;
  price: number;
  quantity?: number;
  totalAmount: number;
  status: any;
  contact?: string;
  remark?: string;
  createTime?: string;
  updateTime?: string;
  createdAt?: string;
  updatedAt?: string;
  completedTime?: string;
  refundStatus?: '退款中' | '已退款' | '已拒绝' | null;
  refundReason?: string;
  refundTime?: string;
  isPending?: boolean;
}

const MyOrdersPage: React.FC = () => {
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState('all');
  const [orders, setOrders] = useState<OrderExt[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedOrder, setSelectedOrder] = useState<OrderExt | null>(null);
  const [reviewVisible, setReviewVisible] = useState(false);
  const [reviewContent, setReviewContent] = useState('');
  const [reviewRating, setReviewRating] = useState(5);
  const [refundApplyVisible, setRefundApplyVisible] = useState(false);
  const [refundReason, setRefundReason] = useState('');
  const [refundRejectVisible, setRefundRejectVisible] = useState(false);
  const [rejectReason, setRejectReason] = useState('');
  const [refundDetail, setRefundDetail] = useState<any>(null);
  const [refundDetailVisible, setRefundDetailVisible] = useState(false);

  // 加载订单
  const loadOrders = useCallback(async () => {
    setLoading(true);
    try {
      const currentUser = getUserInfo();
      if (!currentUser) {
        message.error('请先登录');
        navigate('/login');
        return;
      }

      console.log(
        '[订单页面] 当前用户:',
        currentUser,
        '| 当前标签:',
        activeTab,
      );
      let orderList: OrderExt[] = [];

      // 根据tab筛选
      switch (activeTab) {
        case 'buy':
          {
            // 我买到的：我是买家，且已付款（待发货、待收货、已完成）
            const statusList = ['待发货', '待收货', '已完成'];
            for (const status of statusList) {
              const res = await getOrders({ role: 'buy', status });
              orderList.push(...(res.data?.list || []));
            }
            // 去重
            const orderMap = new Map();
            orderList.forEach((o) => orderMap.set(o.id, o));
            orderList = Array.from(orderMap.values());
          }
          break;
        case 'sell':
          {
            // 我卖出的：我是卖家，有人买了我的（待发货、待收货、已完成）
            const statusList = ['待发货', '待收货', '已完成'];
            for (const status of statusList) {
              const res = await getOrders({ role: 'sell', status });
              orderList.push(...(res.data?.list || []));
            }
            // 去重
            const orderMap = new Map();
            orderList.forEach((o) => orderMap.set(o.id, o));
            orderList = Array.from(orderMap.values());
          }
          break;
        case 'published':
          {
            // 我发布的：还没卖出去的商品和服务
            const [myProductsRes, myServicesRes] = await Promise.all([
              getMyProducts(),
              getMyServices(),
            ]);
            const myProducts =
              myProductsRes.code === 200 ? myProductsRes.data || [] : [];
            const myServices =
              myServicesRes.code === 200 ? myServicesRes.data || [] : [];

            const pendingProducts = myProducts.map((p: any) => ({
              id: `pending-product-${p.id}`,
              orderNo: `PENDING-${p.id}`,
              type: '商品',
              productId: p.id,
              product: p,
              sellerId: currentUser.id,
              price: p.price,
              totalAmount: p.price,
              status: '待售',
              createTime: p.createTime,
              isPending: true,
            }));

            const pendingServices = myServices.map((s: any) => ({
              id: `pending-service-${s.id}`,
              orderNo: `PENDING-S-${s.id}`,
              type: '服务',
              serviceId: s.id,
              service: s,
              sellerId: currentUser.id,
              price: s.price,
              totalAmount: s.price,
              status: '待售',
              createTime: s.createTime,
              isPending: true,
            }));

            orderList = ([...pendingProducts, ...pendingServices] as unknown) as OrderExt[];
          }
          break;
        case 'pending':
          {
            // 进行中：还没确认收货的（待发货、待收货）
            const statusList = ['待发货', '待收货'];
            for (const status of statusList) {
              const buyRes = await getOrders({ role: 'buy', status });
              const sellRes = await getOrders({ role: 'sell', status });
              orderList.push(...(buyRes.data?.list || []));
              orderList.push(...(sellRes.data?.list || []));
            }
            // 去重
            const orderMap = new Map();
            orderList.forEach((o) => orderMap.set(o.id, o));
            orderList = Array.from(orderMap.values());
          }
          break;
        default: {
          // all
          const buyRes = await getOrders({ role: 'buy' });
          const sellRes = await getOrders({ role: 'sell' });
          const buyOrders = buyRes.data?.list || [];
          const sellOrders = sellRes.data?.list || [];
          const orderMap = new Map();
          [...buyOrders, ...sellOrders].forEach((order) => {
            orderMap.set(order.id, order);
          });
          orderList = Array.from(orderMap.values());
        }
      }

      console.log('[订单页面] 加载到订单数量:', orderList.length);

      // 加载商品详情
      for (const order of orderList) {
        if (order.type === '商品' && order.productId) {
          try {
            const productRes = await getProduct(order.productId);
            if (productRes.code === 200 && productRes.data) {
              order.product = productRes.data;
            }
          } catch (e) {
            console.error('获取商品详情失败', e);
          }
        }
      }

      setOrders(orderList);
    } catch (error) {
      console.error('加载订单失败', error);
      message.error('加载订单失败');
    } finally {
      setLoading(false);
    }
  }, [activeTab, navigate]);

  // 初始化加载 + 标签页切换时重新加载
  useEffect(() => {
    loadOrders();
  }, [loadOrders]);

  const handleCancelOrder = (order: OrderExt) => {
    const isPaid = order.status === '待发货';
    Modal.confirm({
      title: '确认取消订单',
      content: isPaid
        ? '买家已付款，取消后金额将退回买家账户，确定要取消吗？'
        : '确定要取消这个订单吗？',
      onOk: async () => {
        try {
          const res = await cancelOrderApi(order.id);
          if (res.code === 200) {
            message.success(isPaid ? '订单已取消，金额已退回' : '订单已取消');
            refreshUserInfo();
            loadOrders();
          } else {
            message.error(res.message || '取消失败');
          }
        } catch (error) {
          message.error('取消订单失败');
        }
      },
    });
  };

  const handleConfirmReceive = (order: OrderExt) => {
    Modal.confirm({
      title: '确认收货',
      content: '请确认您已收到商品/服务，确认后金额将转给卖家，交易完成。',
      onOk: async () => {
        try {
          const res = await confirmReceiveOrder(order.id);
          if (res.code === 200) {
            message.success('交易完成！卖家已收到款项');
            refreshUserInfo();
            setSelectedOrder(order);
            setReviewVisible(true);
            loadOrders();
          } else {
            message.error(res.message || '操作失败');
          }
        } catch (error) {
          message.error('确认收货失败');
        }
      },
    });
  };

  const handleSubmitReview = () => {
    if (selectedOrder) {
      message.success('评价成功！');
      setReviewVisible(false);
      setReviewContent('');
      setReviewRating(5);
    }
  };

  const handleContact = (order: OrderExt) => {
    const currentUser = getUserInfo();
    const targetId =
      order.buyerId === currentUser?.id ? order.sellerId : order.buyerId;
    navigate(`/messages?target=${targetId}`);
  };

  const openRefundApply = (order: OrderExt) => {
    setSelectedOrder(order);
    setRefundReason('');
    setRefundApplyVisible(true);
  };

  const submitRefundApply = async () => {
    if (!selectedOrder) return;
    const trimmed = refundReason.trim();
    if (trimmed.length < 5) {
      message.warning('请填写至少 5 个字的退款原因');
      return;
    }
    if (trimmed.length > 500) {
      message.warning('退款原因不能超过 500 字');
      return;
    }
    try {
      const res = await applyRefund(selectedOrder.id, trimmed);
      if (res.code === 200) {
        message.success('退款申请已提交，等待卖家处理');
        setRefundApplyVisible(false);
        setRefundReason('');
        loadOrders();
      } else {
        message.error(res.message || '申请失败');
      }
    } catch (error) {
      message.error('申请退款失败');
    }
  };

  const openRefundDetail = async (order: OrderExt) => {
    setSelectedOrder(order);
    try {
      const res = await getLatestRefund(order.id);
      if (res.code === 200 && res.data) {
        setRefundDetail(res.data);
      } else {
        setRefundDetail({
          reason: order.refundReason,
          status: order.refundStatus,
          processedTime: order.refundTime,
        });
      }
    } catch {
      setRefundDetail({
        reason: order.refundReason,
        status: order.refundStatus,
        processedTime: order.refundTime,
      });
    }
    setRefundDetailVisible(true);
  };

  const handleApproveRefund = (order: OrderExt) => {
    Modal.confirm({
      title: '同意退款',
      content: `确定同意该订单的退款申请吗？同意后 ¥${order.totalAmount} 将退回买家账户，订单将变为「已退款」状态。`,
      okText: '同意退款',
      okType: 'danger',
      cancelText: '再想想',
      onOk: async () => {
        try {
          const res = await approveRefund(order.id);
          if (res.code === 200) {
            message.success('已同意退款，金额已退回买家');
            refreshUserInfo();
            loadOrders();
          } else {
            message.error(res.message || '操作失败');
          }
        } catch {
          message.error('操作失败');
        }
      },
    });
  };

  const openRefundReject = (order: OrderExt) => {
    setSelectedOrder(order);
    setRejectReason('');
    setRefundRejectVisible(true);
  };

  const submitRefundReject = async () => {
    if (!selectedOrder) return;
    const trimmed = rejectReason.trim();
    if (!trimmed) {
      message.warning('请填写拒绝原因');
      return;
    }
    try {
      const res = await rejectRefund(selectedOrder.id, trimmed);
      if (res.code === 200) {
        message.success('已拒绝退款申请');
        setRefundRejectVisible(false);
        setRejectReason('');
        loadOrders();
      } else {
        message.error(res.message || '操作失败');
      }
    } catch {
      message.error('操作失败');
    }
  };

  const getStatusConfig = (status: string) => {
    if (status === '待售') {
      return { color: 'blue', icon: 'shop' };
    }
    return (
      ORDER_STATUS_CONFIG[status as keyof typeof ORDER_STATUS_CONFIG] || {
        color: '#999',
        icon: 'info',
      }
    );
  };

  const getOrderIcon = (type: string) => {
    return type === '商品' ? <ShopOutlined /> : <UserOutlined />;
  };

  const formatStatus = (order: OrderExt) => {
    if ((order as any).isPending) {
      return '待售中';
    }
    const currentUser = getUserInfo();
    if (order.buyerId === currentUser?.id) {
      return '我买到的';
    } else {
      return '我卖出的';
    }
  };

  const getOrderActions = (order: OrderExt) => {
    const currentUser = getUserInfo();
    if (!currentUser) return [];

    // 待售状态的商品/服务
    if ((order as any).isPending) {
      const link =
        order.type === '商品'
          ? `/product/${order.productId}`
          : `/service/${order.serviceId}`;
      return [{ key: 'view', label: '查看', action: () => navigate(link) }];
    }

    const isBuyer = order.buyerId === currentUser.id;
    const actions: {
      key: string;
      label: string;
      action: () => void;
      type?: string;
    }[] = [];

    switch (order.status) {
      case '待付款':
        actions.push(
          {
            key: 'pay',
            label: '去支付',
              action: async () => {
                try {
                  const res = await payOrder(order.id);
                  if (res.code === 200) {
                    message.success('支付成功，卖家即将发货');
                    refreshUserInfo();
                    loadOrders();
                  } else {
                    message.error(res.message || '支付失败');
                  }
                } catch (error) {
                  message.error('支付失败');
                }
              },
          },
          {
            key: 'cancel',
            label: '取消',
            action: () => handleCancelOrder(order),
            type: 'text',
          },
        );
        break;
      case '待发货':
        if (isBuyer) {
          actions.push(
            {
              key: 'contact',
              label: '联系卖家',
              action: () => handleContact(order),
            },
            {
              key: 'refund',
              label: '申请退款',
              action: () => openRefundApply(order),
            },
          );
        } else {
          actions.push(
            {
              key: 'ship',
              label: '确认发货',
              action: async () => {
                try {
                  const res = await shipOrder(order.id);
                  if (res.code === 200) {
                    message.success('已确认发货，等待买家确认收货');
                    refreshUserInfo();
                    loadOrders();
                  } else {
                    message.error(res.message || '操作失败');
                  }
                } catch (error) {
                  message.error('确认发货失败');
                }
              },
            },
            {
              key: 'cancel',
              label: '取消订单',
              action: () => handleCancelOrder(order),
              type: 'text',
            },
          );
        }
        break;
      case '待收货':
        if (isBuyer) {
          actions.push(
            {
              key: 'receive',
              label: '确认收货',
              action: () => handleConfirmReceive(order),
            },
            {
              key: 'contact',
              label: '联系卖家',
              action: () => handleContact(order),
            },
            {
              key: 'refund',
              label: '申请退款',
              action: () => openRefundApply(order),
            },
          );
        }
        break;
      case '退款中':
        if (isBuyer) {
          actions.push({
            key: 'refundDetail',
            label: '退款详情',
            action: () => openRefundDetail(order),
          });
        } else {
          actions.push(
            {
              key: 'approve',
              label: '同意退款',
              action: () => handleApproveRefund(order),
            },
            {
              key: 'reject',
              label: '拒绝退款',
              action: () => openRefundReject(order),
              type: 'text',
            },
            {
              key: 'refundDetail',
              label: '查看原因',
              action: () => openRefundDetail(order),
              type: 'text',
            },
          );
        }
        break;
      case '已退款':
        actions.push({
          key: 'refundDetail',
          label: '退款详情',
          action: () => openRefundDetail(order),
        });
        break;
      case '已完成':
        actions.push({
          key: 'review',
          label: '去评价',
          action: () => {
            setSelectedOrder(order);
            setReviewVisible(true);
          },
        });
        break;
    }

    return actions;
  };

  const formatDate = (dateStr: string | undefined) => {
    if (!dateStr) return '';
    try {
      const date = new Date(dateStr);
      return date.toLocaleString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
      });
    } catch {
      return dateStr;
    }
  };

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <h1 className={styles.title}>我的订单</h1>
      </div>

      <Card className={styles.mainCard}>
        <Tabs
          activeKey={activeTab}
          onChange={(key) => {
            setActiveTab(key);
          }}
          className={styles.tabs}
          items={[
            { key: 'all', label: '全部订单' },
            { key: 'buy', label: '我买到的' },
            { key: 'sell', label: '我卖出的' },
            { key: 'published', label: '我发布的' },
            { key: 'pending', label: '进行中' },
          ]}
        />

        {orders.length === 0 ? (
          <Empty description="暂无订单" className={styles.empty} />
        ) : (
          <div className={styles.orderList}>
            {orders.map((order) => (
              <div key={order.id} className={styles.orderCard}>
                <div className={styles.orderHeader}>
                  <div className={styles.orderMeta}>
                    <span className={styles.orderNo}>
                      订单号: {order.orderNo}
                    </span>
                    <Tag
                      icon={getOrderIcon(order.type)}
                      color="purple"
                      className={styles.typeTag}
                    >
                      {order.type}
                    </Tag>
                    <Tag
                      color={getStatusConfig(order.status).color}
                      className={styles.statusTag}
                    >
                      {order.status}
                    </Tag>
                    <Tag className={styles.roleTag}>{formatStatus(order)}</Tag>
                  </div>
                  <span className={styles.orderTime}>
                    <ClockCircleOutlined /> {formatDate(order.createTime)}
                  </span>
                </div>

                <div className={styles.orderContent}>
                  {order.refundReason &&
                    (order.status === '退款中' ||
                      order.status === '已退款' ||
                      order.status === '待发货' ||
                      order.status === '待收货') && (
                      <Alert
                        className={styles.refundAlert}
                        type={
                          order.status === '已退款'
                            ? 'success'
                            : order.status === '退款中'
                            ? 'warning'
                            : 'info'
                        }
                        showIcon
                        message={
                          order.status === '已退款'
                            ? `已退款：${order.refundReason}`
                            : order.status === '退款中'
                            ? `退款申请中：${order.refundReason}`
                            : `退款已拒绝：${order.refundReason}`
                        }
                      />
                    )}
                  <div className={styles.productInfo}>
                    {order.product && (
                      <Link
                        to={`/product/${order.productId}`}
                        className={styles.productLink}
                      >
                        <ProductImage
                          src={order.product.images}
                          alt={order.product.title}
                          className={styles.productImage}
                        />
                        <div className={styles.productDetail}>
                          <span className={styles.productTitle}>
                            {order.product.title}
                          </span>
                          <span className={styles.productDesc}>
                            {order.product.description?.slice(0, 50)}...
                          </span>
                        </div>
                      </Link>
                    )}
                    {order.service && (
                      <div className={styles.serviceInfo}>
                        <div className={styles.serviceType}>
                          {order.service.serviceType}
                        </div>
                        <span className={styles.productTitle}>
                          {order.service.title}
                        </span>
                      </div>
                    )}
                    {!order.product && !order.service && (
                      <div className={styles.productDetail}>
                        <span className={styles.productTitle}>商品已下架</span>
                      </div>
                    )}
                  </div>

                  <div className={styles.orderRight}>
                    <div className={styles.userInfo}>
                      <Avatar
                        size={32}
                        src={order.seller?.avatar}
                        icon={<UserOutlined />}
                      />
                      <span>
                        {order.buyerId === getUserInfo()?.id
                          ? order.seller?.nickname
                          : order.buyer?.nickname}
                      </span>
                    </div>
                    <div className={styles.orderAmount}>
                      <span className={styles.amountLabel}>实付款</span>
                      <span className={styles.amountValue}>
                        ¥{order.totalAmount}
                      </span>
                    </div>
                  </div>
                </div>

                <div className={styles.orderFooter}>
                  <div className={styles.orderContact}>
                    {order.remark && (
                      <span className={styles.orderRemark}>
                        备注: {order.remark}
                      </span>
                    )}
                  </div>
                  <div className={styles.orderActions}>
                    {getOrderActions(order).map((action) => (
                      <Button
                        key={action.key}
                        size="small"
                        type={action.type === 'text' ? 'text' : 'default'}
                        onClick={action.action}
                      >
                        {action.label}
                      </Button>
                    ))}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </Card>

      {/* 评价弹窗 */}
      <Modal
        title="发表评价"
        open={reviewVisible}
        onCancel={() => setReviewVisible(false)}
        onOk={handleSubmitReview}
        okText="提交评价"
      >
        {selectedOrder && (
          <div className={styles.reviewForm}>
            <div className={styles.reviewProduct}>
              <ProductImage
                src={selectedOrder.product?.images}
                alt={
                  selectedOrder.product?.title ||
                  selectedOrder.service?.title ||
                  '商品'
                }
                style={{
                  width: 60,
                  height: 60,
                  borderRadius: 4,
                  flexShrink: 0,
                }}
              />
              <span>
                {selectedOrder.product?.title ||
                  selectedOrder.service?.title ||
                  '商品'}
              </span>
            </div>
            <Divider />
            <div className={styles.reviewRating}>
              <span>评分:</span>
              <Rate value={reviewRating} onChange={setReviewRating} />
            </div>
            <div className={styles.reviewContent}>
              <span>评价内容:</span>
              <TextArea
                rows={4}
                placeholder="分享你的购买体验..."
                value={reviewContent}
                onChange={(e) => setReviewContent(e.target.value)}
              />
            </div>
          </div>
        )}
      </Modal>

      {/* 申请退款弹窗 */}
      <Modal
        title="申请退款"
        open={refundApplyVisible}
        onCancel={() => setRefundApplyVisible(false)}
        onOk={submitRefundApply}
        okText="提交申请"
        cancelText="取消"
        width={520}
      >
        {selectedOrder && (
          <div className={styles.refundForm}>
            <Alert
              className={styles.refundTip}
              type="info"
              showIcon
              message="提交后将通知卖家，卖家同意后退款将原路退回您的账户余额。"
            />
            <div className={styles.refundSummary}>
              <div className={styles.refundSummaryRow}>
                <span>订单号</span>
                <span>{selectedOrder.orderNo}</span>
              </div>
              <div className={styles.refundSummaryRow}>
                <span>退款金额</span>
                <span className={styles.refundAmount}>
                  ¥{selectedOrder.totalAmount}
                </span>
              </div>
            </div>
            <div className={styles.refundField}>
              <span className={styles.refundLabel}>
                退款原因 <em>*</em>
              </span>
              <TextArea
                rows={4}
                placeholder="请详细说明退款原因（5-500 字），例如：商品与描述不符 / 收到货有破损 / 不需要了..."
                value={refundReason}
                onChange={(e) => setRefundReason(e.target.value)}
                maxLength={500}
                showCount
              />
            </div>
          </div>
        )}
      </Modal>

      {/* 拒绝退款弹窗 */}
      <Modal
        title="拒绝退款申请"
        open={refundRejectVisible}
        onCancel={() => setRefundRejectVisible(false)}
        onOk={submitRefundReject}
        okText="确认拒绝"
        okType="danger"
        cancelText="取消"
        width={520}
      >
        {selectedOrder && (
          <div className={styles.refundForm}>
            <Alert
              className={styles.refundTip}
              type="warning"
              showIcon
              message="拒绝后订单将回到原状态，买家可以继续走发货/收货流程。请认真填写拒绝原因以减少纠纷。"
            />
            <div className={styles.refundSummary}>
              <div className={styles.refundSummaryRow}>
                <span>订单号</span>
                <span>{selectedOrder.orderNo}</span>
              </div>
              <div className={styles.refundSummaryRow}>
                <span>买家退款原因</span>
                <span>{selectedOrder.refundReason || '未填写'}</span>
              </div>
            </div>
            <div className={styles.refundField}>
              <span className={styles.refundLabel}>
                拒绝原因 <em>*</em>
              </span>
              <TextArea
                rows={4}
                placeholder="请说明拒绝退款的原因，例如：商品已发出且无质量问题 / 已与买家沟通协商..."
                value={rejectReason}
                onChange={(e) => setRejectReason(e.target.value)}
                maxLength={500}
                showCount
              />
            </div>
          </div>
        )}
      </Modal>

      {/* 退款详情弹窗 */}
      <Modal
        title="退款详情"
        open={refundDetailVisible}
        onCancel={() => setRefundDetailVisible(false)}
        footer={[
          <Button
            key="ok"
            type="primary"
            onClick={() => setRefundDetailVisible(false)}
          >
            关闭
          </Button>,
        ]}
        width={520}
      >
        {refundDetail && (
          <div className={styles.refundDetail}>
            <div className={styles.refundSummary}>
              <div className={styles.refundSummaryRow}>
                <span>退款单号</span>
                <span>{refundDetail.refundNo || '-'}</span>
              </div>
              <div className={styles.refundSummaryRow}>
                <span>退款金额</span>
                <span className={styles.refundAmount}>
                  ¥{refundDetail.amount ?? selectedOrder?.totalAmount}
                </span>
              </div>
              <div className={styles.refundSummaryRow}>
                <span>当前状态</span>
                <Tag
                  color={
                    refundDetail.status === '已退款'
                      ? 'green'
                      : refundDetail.status === '已拒绝'
                      ? 'red'
                      : 'orange'
                  }
                >
                  {refundDetail.status || selectedOrder?.refundStatus || '-'}
                </Tag>
              </div>
              <div className={styles.refundSummaryRow}>
                <span>申请原因</span>
                <span>{refundDetail.reason || '-'}</span>
              </div>
              {refundDetail.rejectReason && (
                <div className={styles.refundSummaryRow}>
                  <span>拒绝原因</span>
                  <span>{refundDetail.rejectReason}</span>
                </div>
              )}
              <div className={styles.refundSummaryRow}>
                <span>申请时间</span>
                <span>{formatDate(refundDetail.createTime)}</span>
              </div>
              {refundDetail.processedTime && (
                <div className={styles.refundSummaryRow}>
                  <span>处理时间</span>
                  <span>{formatDate(refundDetail.processedTime)}</span>
                </div>
              )}
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
};

export default MyOrdersPage;
