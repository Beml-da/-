import ProductImage from '@/components/ProductImage';
import { searchAll } from '@/utils/api';
import {
  AppstoreOutlined,
  ArrowLeftOutlined,
  SortAscendingOutlined,
  ToolOutlined,
} from '@ant-design/icons';
import { Link, useNavigate, useSearchParams } from '@umijs/max';
import { Empty, Select, Spin, Tabs } from 'antd';
import { useEffect, useState } from 'react';
import styles from './index.less';

const SearchPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [keyword, setKeyword] = useState(searchParams.get('keyword') || '');
  const [loading, setLoading] = useState(false);
  const [allItems, setAllItems] = useState<any[]>([]);
  const [total, setTotal] = useState(0);
  const [sortBy, setSortBy] = useState('newest');

  useEffect(() => {
    const kw = searchParams.get('keyword') || '';
    setKeyword(kw);
    if (kw) {
      loadData(kw);
    } else {
      setAllItems([]);
      setTotal(0);
    }
  }, [searchParams, sortBy]);

  const loadData = async (kw: string) => {
    setLoading(true);
    try {
      const res = await searchAll({
        keyword: kw,
        sortBy,
        page: 1,
        pageSize: 50,
      });
      if (res.code === 200 && res.data) {
        const data = Array.isArray(res.data) ? res.data : res.data.list || [];
        setAllItems(data);
        setTotal(
          Array.isArray(res.data)
            ? res.total || data.length
            : res.data.total || 0,
        );
      }
    } catch (error) {
      console.error('搜索失败', error);
    } finally {
      setLoading(false);
    }
  };

  const parseJsonArray = (val: any): string[] => {
    if (!val) return [];
    if (Array.isArray(val)) return val;
    try {
      return JSON.parse(val);
    } catch {
      return [];
    }
  };

  // 按 type 分类
  const products = allItems.filter(
    (item) => item.type === 'product' || !item.type,
  );
  const services = allItems.filter((item) => item.type === 'service');

  function renderProductList() {
    if (loading) return null;
    if (products.length === 0) {
      return <Empty description="暂无商品" style={{ marginTop: 60 }} />;
    }
    return (
      <div className={styles.productGrid}>
        {products.map((product) => {
          const images = parseJsonArray(product.images);
          return (
            <Link
              key={product.id}
              to={`/product/${product.id}`}
              className={styles.productCard}
            >
              <ProductImage
                src={images}
                alt={product.title}
                style={{
                  width: '100%',
                  paddingTop: '100%',
                  position: 'relative',
                  display: 'block',
                }}
              />
              {product.condition && (
                <span className={styles.condition}>{product.condition}</span>
              )}
              <div className={styles.productInfo}>
                <h3 className={styles.productTitle}>{product.title}</h3>
                <div className={styles.productPrice}>
                  <span className={styles.price}>¥{product.price}</span>
                  {product.originalPrice && (
                    <span className={styles.originalPrice}>
                      ¥{product.originalPrice}
                    </span>
                  )}
                </div>
                <div className={styles.productMeta}>
                  {product.location && (
                    <span>
                      {product.location.split(' - ').pop() || product.location}
                    </span>
                  )}
                </div>
              </div>
            </Link>
          );
        })}
      </div>
    );
  }

  function renderServiceList() {
    if (loading) return null;
    if (services.length === 0) {
      return <Empty description="暂无服务" style={{ marginTop: 60 }} />;
    }
    return (
      <div className={styles.productGrid}>
        {services.map((service) => {
          const images = parseJsonArray(service.images);
          return (
            <Link
              key={service.id}
              to={`/service/${service.id}`}
              className={styles.productCard}
            >
              <ProductImage
                src={images}
                alt={service.title}
                style={{
                  width: '100%',
                  paddingTop: '100%',
                  position: 'relative',
                  display: 'block',
                }}
              />
              <div className={styles.productInfo}>
                <h3 className={styles.productTitle}>{service.title}</h3>
                <div className={styles.productPrice}>
                  <span className={styles.price}>¥{service.price}</span>
                  {service.priceUnit && (
                    <span style={{ fontSize: 12, color: '#999' }}>
                      {service.priceUnit}
                    </span>
                  )}
                </div>
                <div className={styles.productMeta}>
                  {service.location && (
                    <span>
                      {service.location.split(' - ').pop() || service.location}
                    </span>
                  )}
                </div>
              </div>
            </Link>
          );
        })}
      </div>
    );
  }

  const productItems = [
    {
      key: 'products',
      label: (
        <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <AppstoreOutlined />
          商品{' '}
          <span style={{ fontSize: 12, color: '#999' }}>
            ({products.length})
          </span>
        </span>
      ),
      children: renderProductList(),
    },
    {
      key: 'services',
      label: (
        <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <ToolOutlined />
          服务{' '}
          <span style={{ fontSize: 12, color: '#999' }}>
            ({services.length})
          </span>
        </span>
      ),
      children: renderServiceList(),
    },
  ];

  return (
    <div className={styles.pageWrapper}>
      {/* 顶部搜索区 */}
      <div className={styles.searchBar}>
        <button
          className={styles.backBtn}
          onClick={() => navigate(-1)}
        >
          <ArrowLeftOutlined />
          返回
        </button>
        <div className={styles.keywordDisplay}>
          搜索「<span className={styles.keyword}>{keyword}</span>」
        </div>
      </div>

      {/* 搜索结果区 */}
      {keyword ? (
        <div className={styles.resultArea}>
          <div className={styles.resultHeader}>
            <div className={styles.resultCount}>
              共找到 <span className={styles.total}>{total}</span> 条结果
            </div>
            <div className={styles.resultTools}>
              <Select
                value={sortBy}
                onChange={setSortBy}
                style={{ width: 150 }}
                options={[
                  { label: '默认排序', value: 'default' },
                  { label: '价格从低到高', value: 'price_asc' },
                  { label: '价格从高到低', value: 'price_desc' },
                  { label: '热门优先', value: 'hot' },
                  { label: '最新发布', value: 'newest' },
                ]}
                prefix={<SortAscendingOutlined />}
              />
            </div>
          </div>

          <Spin spinning={loading}>
            <Tabs items={productItems} />
          </Spin>
        </div>
      ) : (
        <Empty description="请输入关键词搜索" style={{ marginTop: 80 }} />
      )}
    </div>
  );
};

export default SearchPage;
