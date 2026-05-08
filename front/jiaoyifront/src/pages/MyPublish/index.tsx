import ProductImage from '@/components/ProductImage';
import { CONDITION_OPTIONS } from '@/constants/campus';
import {
  Product,
  Service,
  deleteProduct,
  deleteService,
  getMyProducts,
  getMyServices,
  updateProductStatus,
  updateServiceStatus,
} from '@/utils/api';
import {
  DeleteOutlined,
  DownCircleOutlined,
  EditOutlined,
  EyeOutlined,
  PlayCircleOutlined,
  PlusOutlined,
} from '@ant-design/icons';
import { Link, useNavigate } from '@umijs/max';
import {
  Button,
  Card,
  Empty,
  Modal,
  Space,
  Table,
  Tabs,
  Tag,
  message,
} from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';
import styles from './index.less';

// 解析后端返回的 images/tags 字段（后端存的是 JSON 字符串）
function parseJsonArray(val: any): string[] {
  if (!val) return [];
  if (Array.isArray(val)) return val;
  try {
    return JSON.parse(val);
  } catch {
    return [];
  }
}

const MyPublishPage: React.FC = () => {
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState('products');
  const [products, setProducts] = useState<Product[]>([]);
  const [services, setServices] = useState<Service[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    loadProducts();
    loadServices();
  }, []);

  useEffect(() => {
    if (activeTab === 'products') {
      loadProducts();
    } else {
      loadServices();
    }
  }, [activeTab]);

  const loadProducts = async () => {
    setLoading(true);
    try {
      const res = await getMyProducts();
      if (res.code === 200) {
        setProducts(
          res.data.map((p: any) => ({
            ...p,
            images: parseJsonArray(p.images),
            tags: parseJsonArray(p.tags),
          })),
        );
      }
    } catch {
      message.error('加载商品失败');
    } finally {
      setLoading(false);
    }
  };

  const loadServices = async () => {
    try {
      const res = await getMyServices();
      if (res.code === 200) {
        setServices(
          res.data.map((s: any) => ({
            ...s,
            tags: parseJsonArray(s.tags),
          })),
        );
      }
    } catch {
      message.error('加载服务失败');
    }
  };

  const handleDelete = async (record: Product | Service) => {
    Modal.confirm({
      title: '确认删除',
      content: '确定要删除这个发布吗？删除后不可恢复。',
      okText: '确认删除',
      okType: 'danger',
      onOk: async () => {
        try {
          if ('images' in record) {
            await deleteProduct(record.id);
            setProducts((prev) => prev.filter((p) => p.id !== record.id));
          } else {
            await deleteService(record.id);
            setServices((prev) => prev.filter((s) => s.id !== record.id));
          }
          message.success('删除成功');
        } catch {
          message.error('删除失败');
        }
      },
    });
  };

  const handleEdit = () => {
    message.info('编辑功能开发中...');
  };

  const handleToggleStatus = useCallback(async (record: Product | Service) => {
    const isProduct = (record as any).type !== 'service';

    if (isProduct) {
      const newStatus = record.status === '在售' ? '已下架' : '在售';
      try {
        await updateProductStatus(record.id, newStatus);
        setProducts((prev) =>
          prev.map((p) =>
            p.id === record.id ? { ...p, status: newStatus as any } : p,
          ),
        );
        message.success('状态更新成功');
      } catch {
        message.error('状态更新失败');
      }
    } else {
      // 兼容：后端可能存商品状态值('在售'/'已下架')也可能存服务状态值('可用'/'暂停')
      const currentStatus = record.status;
      let newStatus: string;
      if (currentStatus === '可用') {
        newStatus = '暂停';
      } else if (currentStatus === '暂停') {
        newStatus = '可用';
      } else if (currentStatus === '已下架') {
        newStatus = '在售';
      } else {
        newStatus = '已下架'; // 默认下架（'在售' -> '已下架'）
      }
      setServices((prev) =>
        prev.map((s) =>
          s.id === record.id
            ? { ...s, status: newStatus as Service['status'] }
            : s,
        ),
      );
      try {
        await updateServiceStatus(record.id, newStatus);
        message.success('状态更新成功');
      } catch {
        setServices((prev) =>
          prev.map((s) =>
            s.id === record.id
              ? { ...s, status: currentStatus as Service['status'] }
              : s,
          ),
        );
        message.error('状态更新失败');
      }
    }
  }, []);

  // 商品列配置
  const productColumns = useMemo(
    () => [
      {
        title: '商品',
        key: 'product',
        render: (_: any, record: Product) => {
          return (
            <div className={styles.productCell}>
              <ProductImage
                src={record.images}
                alt={record.title}
                width={60}
                height={60}
              />
              <div className={styles.productInfo}>
                <Link
                  to={`/product/${record.id}`}
                  className={styles.productTitle}
                >
                  {record.title}
                </Link>
                <div className={styles.productMeta}>
                  <Tag
                    color={
                      CONDITION_OPTIONS.find(
                        (c) => c.value === record.condition,
                      )?.color
                    }
                  >
                    {record.condition}
                  </Tag>
                  <span className={styles.productPrice}>¥{record.price}</span>
                </div>
              </div>
            </div>
          );
        },
      },
      {
        title: '浏览',
        dataIndex: 'viewCount',
        key: 'viewCount',
        width: 80,
      },
      {
        title: '收藏',
        dataIndex: 'favoriteCount',
        key: 'favoriteCount',
        width: 80,
      },
      {
        title: '状态',
        dataIndex: 'status',
        key: 'status',
        width: 100,
        render: (status: string) => (
          <Tag
            color={
              status === '在售'
                ? 'success'
                : status === '已售出'
                ? 'blue'
                : 'default'
            }
          >
            {status}
          </Tag>
        ),
      },
      {
        title: '发布时间',
        dataIndex: 'createTime',
        key: 'createTime',
        width: 120,
      },
      {
        title: '操作',
        key: 'action',
        width: 200,
        render: (_: any, record: Product) => (
          <Space size="small">
            <Button
              type="text"
              size="small"
              icon={<EyeOutlined />}
              onClick={() => navigate(`/product/${record.id}`)}
            >
              查看
            </Button>
            <Button
              type="text"
              size="small"
              icon={
                record.status === '在售' ? (
                  <DownCircleOutlined />
                ) : (
                  <PlayCircleOutlined />
                )
              }
              onClick={() => handleToggleStatus(record)}
            >
              {record.status === '在售' ? '下架' : '上架'}
            </Button>
            <Button
              type="text"
              size="small"
              icon={<EditOutlined />}
              onClick={() => handleEdit()}
            >
              编辑
            </Button>
            <Button
              type="text"
              size="small"
              danger
              icon={<DeleteOutlined />}
              onClick={() => handleDelete(record)}
            />
          </Space>
        ),
      },
    ],
    [navigate],
  );

  // 服务列配置
  const serviceColumns = useMemo(
    () => [
      {
        title: '服务',
        key: 'service',
        render: (_: any, record: Service) => {
          const serviceType = record.serviceType || '服务';
          return (
            <div className={styles.productCell}>
              <div className={styles.serviceIcon}>{serviceType}</div>
              <div className={styles.productInfo}>
                <Link
                  to={`/service/${record.id}`}
                  className={styles.productTitle}
                >
                  {record.title}
                </Link>
                <div className={styles.productMeta}>
                  <Tag color="purple">{serviceType}</Tag>
                  <span className={styles.productPrice}>¥{record.price}</span>
                </div>
              </div>
            </div>
          );
        },
      },
      {
        title: '浏览',
        dataIndex: 'viewCount',
        key: 'viewCount',
        width: 80,
      },
      {
        title: '收藏',
        dataIndex: 'favoriteCount',
        key: 'favoriteCount',
        width: 80,
      },
      {
        title: '评分',
        dataIndex: 'rating',
        key: 'rating',
        width: 100,
        render: (rating: number) => (
          <span className={styles.rating}>★ {rating || 0}</span>
        ),
      },
      {
        title: '状态',
        dataIndex: 'status',
        key: 'status',
        width: 100,
        render: (status: string) => (
          <Tag color={status === '可用' ? 'success' : 'warning'}>{status}</Tag>
        ),
      },
      {
        title: '发布时间',
        dataIndex: 'createTime',
        key: 'createTime',
        width: 120,
      },
      {
        title: '操作',
        key: 'action',
        width: 200,
        render: (_: any, record: Service) => (
          <Space size="small">
            <Button
              type="text"
              size="small"
              icon={<EyeOutlined />}
              onClick={() => navigate(`/service/${record.id}`)}
            >
              查看
            </Button>
            <Button
              type="text"
              size="small"
              icon={
                (record.status as any) === '可用' ||
                (record.status as any) === '在售' ? (
                  <DownCircleOutlined />
                ) : (
                  <PlayCircleOutlined />
                )
              }
              onClick={() => handleToggleStatus(record)}
            >
              {(record.status as any) === '可用' ||
              (record.status as any) === '在售'
                ? '下架'
                : '上架'}
            </Button>
            <Button
              type="text"
              size="small"
              icon={<EditOutlined />}
              onClick={() => message.info('编辑功能开发中...')}
            >
              编辑
            </Button>
            <Button
              type="text"
              size="small"
              danger
              icon={<DeleteOutlined />}
              onClick={() => handleDelete(record)}
            />
          </Space>
        ),
      },
    ],
    [navigate, handleToggleStatus],
  );

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <h1 className={styles.title}>我的发布</h1>
        <Link to="/publish">
          <Button type="primary" icon={<PlusOutlined />} size="large">
            发布新宝贝
          </Button>
        </Link>
      </div>

      <Card className={styles.mainCard}>
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          items={[
            {
              key: 'products',
              label: `商品 (${products.length})`,
              children:
                products.length === 0 ? (
                  <Empty
                    description="还没有发布商品"
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                  >
                    <Link to="/publish">
                      <Button type="primary">发布商品</Button>
                    </Link>
                  </Empty>
                ) : (
                  <Table
                    key={products.length}
                    columns={productColumns}
                    dataSource={products}
                    rowKey="id"
                    loading={loading}
                    pagination={{ pageSize: 10 }}
                    className={styles.table}
                  />
                ),
            },
            {
              key: 'services',
              label: `服务 (${services.length})`,
              children:
                services.length === 0 ? (
                  <Empty
                    description="还没有发布服务"
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                  >
                    <Link to="/publish?type=service">
                      <Button type="primary">发布服务</Button>
                    </Link>
                  </Empty>
                ) : (
                  <Table
                    key={services.length}
                    columns={serviceColumns}
                    dataSource={services}
                    rowKey="id"
                    loading={loading}
                    pagination={{ pageSize: 10 }}
                    className={styles.table}
                  />
                ),
            },
          ]}
        />
      </Card>
    </div>
  );
};

export default MyPublishPage;
