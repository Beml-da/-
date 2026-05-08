import ProductImage from '@/components/ProductImage';
import { HOT_SEARCH_KEYWORDS, PRODUCT_CATEGORIES } from '@/constants/campus';
import {
  getHotProducts,
  getNewestProducts,
  getSearchSuggestions,
  getServices,
  Product,
  SearchSuggestion,
  Service,
} from '@/utils/api';
import {
  AppstoreOutlined,
  BookOutlined,
  ClockCircleOutlined,
  CloudOutlined,
  FireOutlined,
  InboxOutlined,
  LaptopOutlined,
  MoreOutlined,
  PrinterOutlined,
  RightOutlined,
  SearchOutlined,
  SendOutlined,
  ShoppingCartOutlined,
  ShoppingOutlined,
  SkinOutlined,
  TeamOutlined,
  ToolOutlined,
} from '@ant-design/icons';
import { Link, useNavigate } from '@umijs/max';
import { AutoComplete, Button, Input } from 'antd';
import { useEffect, useState } from 'react';
import styles from './index.less';

// 服务类型对应的配置
const SERVICE_TYPE_CONFIG: Record<
  string,
  { bg: string; textColor: string; icon: React.ReactNode }
> = {
  取快递: {
    bg: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    textColor: '#fff',
    icon: <InboxOutlined />,
  },
  带外卖: {
    bg: 'linear-gradient(135deg, #f5576c 0%, #f093fb 100%)',
    textColor: '#fff',
    icon: <ShoppingOutlined />,
  },
  代买: {
    bg: 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)',
    textColor: '#fff',
    icon: <ShoppingCartOutlined />,
  },
  打印: {
    bg: 'linear-gradient(135deg, #38f9d7 0%, #43e97b 100%)',
    textColor: '#333',
    icon: <PrinterOutlined />,
  },
  跑腿: {
    bg: 'linear-gradient(135deg, #fa709a 0%, #fee140 100%)',
    textColor: '#fff',
    icon: <SendOutlined />,
  },
  其他: {
    bg: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    textColor: '#fff',
    icon: <ToolOutlined />,
  },
};

const getServiceConfig = (serviceType: string) => {
  return SERVICE_TYPE_CONFIG[serviceType] || SERVICE_TYPE_CONFIG['其他'];
};

const HomePage: React.FC = () => {
  const [hotProducts, setHotProducts] = useState<Product[]>([]);
  const [latestProducts, setLatestProducts] = useState<Product[]>([]);
  const [services, setServices] = useState<Service[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchValue, setSearchValue] = useState('');
  const [suggestions, setSuggestions] = useState<SearchSuggestion[]>([]);
  const navigate = useNavigate();

  useEffect(() => {
    const loadData = async () => {
      setLoading(true);
      try {
        const [hotRes, newestRes, serviceRes] = await Promise.all([
          getHotProducts(6),
          getNewestProducts(6),
          getServices({ pageSize: 6 }),
        ]);

        if (hotRes.code === 200 && hotRes.data) {
          const list = Array.isArray(hotRes.data)
            ? hotRes.data
            : (hotRes.data as any).list || [];
          setHotProducts(list);
        }
        if (newestRes.code === 200 && newestRes.data) {
          const list = Array.isArray(newestRes.data)
            ? newestRes.data
            : (newestRes.data as any).list || [];
          setLatestProducts(list);
        }
        if (serviceRes.code === 200 && serviceRes.data) {
          const list = Array.isArray(serviceRes.data)
            ? serviceRes.data
            : (serviceRes.data as any).list || [];
          setServices(list);
        }
      } catch (error) {
        console.error('加载首页数据失败', error);
      } finally {
        setLoading(false);
      }
    };

    loadData();
  }, []);

  // 搜索建议
  useEffect(() => {
    if (!searchValue.trim()) {
      setSuggestions([]);
      return;
    }
    const timer = setTimeout(async () => {
      try {
        const res = await getSearchSuggestions(searchValue, 10);
        if (res.code === 200 && res.data) {
          setSuggestions(Array.isArray(res.data) ? res.data : []);
        }
      } catch (e) {
        console.error('搜索建议失败', e);
      }
    }, 300);
    return () => clearTimeout(timer);
  }, [searchValue]);

  const handleSearch = (value: string) => {
    if (!value.trim()) return;
    setSuggestions([]);
    navigate(`/search?keyword=${encodeURIComponent(value.trim())}`);
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

  const suggestionOptions = suggestions.map((item) => ({
    label: (
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 10,
          padding: '4px 0',
        }}
      >
        {item.coverImage && (
          <img
            src={item.coverImage}
            alt=""
            style={{
              width: 36,
              height: 36,
              objectFit: 'cover',
              borderRadius: 4,
              flexShrink: 0,
            }}
          />
        )}
        <div style={{ overflow: 'hidden' }}>
          <div
            style={{
              whiteSpace: 'nowrap',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              fontSize: 14,
            }}
          >
            {item.title}
          </div>
          <div style={{ fontSize: 12, color: '#999' }}>¥{item.price}</div>
        </div>
      </div>
    ),
    value: item.title,
    keyword: item.title,
  }));

  return (
    <div className={styles.container}>
      {/* 搜索区域 */}
      <div className={styles.searchSection}>
        <div className={styles.searchBox}>
          <AutoComplete
            className={styles.searchInput}
            value={searchValue}
            options={suggestionOptions}
            onSearch={(val) => setSearchValue(val)}
            onSelect={(val) => handleSearch(val)}
            onChange={(val) => setSearchValue(val)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                handleSearch(searchValue);
              }
            }}
            filterOption={false}
            dropdownMatchSelectWidth={400}
            style={{ width: '100%' }}
          >
            <Input
              size="large"
              placeholder="搜索商品、服务..."
              prefix={<SearchOutlined style={{ color: '#999' }} />}
              style={{ border: 'none' }}
            />
          </AutoComplete>
          <Button
            type="primary"
            size="large"
            className={styles.searchBtn}
            onClick={() => handleSearch(searchValue)}
          >
            搜索
          </Button>
        </div>
        <div className={styles.hotKeywords}>
          <span className={styles.hotLabel}>热门：</span>
          {HOT_SEARCH_KEYWORDS.slice(0, 6).map((keyword, index) => (
            <Link
              key={index}
              to={`/search?keyword=${encodeURIComponent(keyword)}`}
              className={styles.keyword}
            >
              {keyword}
            </Link>
          ))}
        </div>
      </div>

      {/* 分类快捷入口 */}
      <div className={styles.section}>
        <div className={styles.sectionHeader}>
          <h2 className={styles.sectionTitle}>
            <AppstoreOutlined /> 全部分类
          </h2>
          <Link to="/categories" className={styles.moreLink}>
            查看更多 <RightOutlined />
          </Link>
        </div>
        <div className={styles.categoryGrid}>
          {PRODUCT_CATEGORIES.map((category) => (
            <Link
              key={category.id}
              to={`/categories?category=${encodeURIComponent(category.name)}`}
              className={styles.categoryCard}
              style={
                { '--category-color': category.color } as React.CSSProperties
              }
            >
              <div className={styles.categoryIcon}>
                <CategoryIcon type={category.icon} />
              </div>
              <span className={styles.categoryName}>{category.name}</span>
            </Link>
          ))}
        </div>
      </div>

      {/* 校园服务 */}
      <div className={styles.section}>
        <div className={styles.sectionHeader}>
          <h2 className={styles.sectionTitle}>
            <TeamOutlined /> 校园服务
          </h2>
          <Link to="/categories?category=校园服务" className={styles.moreLink}>
            查看更多 <RightOutlined />
          </Link>
        </div>
        <div className={styles.serviceGrid}>
          {services.map((service: any) => {
            const config = getServiceConfig(service.serviceType);
            return (
              <Link
                key={service.id}
                to={`/service/${service.id}`}
                className={styles.serviceCard}
              >
                <div
                  className={styles.serviceImage}
                  style={{ background: config.bg }}
                >
                  <div
                    className={styles.serviceIconWrap}
                    style={{ color: config.textColor }}
                  >
                    {config.icon}
                  </div>
                  <span className={styles.serviceTypeTag}>
                    {service.serviceType || '服务'}
                  </span>
                </div>
                <div className={styles.serviceContent}>
                  <p className={styles.serviceTitle}>{service.title}</p>
                  <p className={styles.serviceDesc}>{service.description}</p>
                  <div className={styles.serviceFooter}>
                    <span className={styles.servicePrice}>
                      ¥{service.price}
                      {service.priceUnit || '/次'}
                    </span>
                    <span className={styles.serviceOrders}>
                      ★ {(service.rating || 5).toFixed(1)} ·{' '}
                      {service.orderCount || 0}单
                    </span>
                  </div>
                </div>
              </Link>
            );
          })}
          {services.length === 0 && !loading && (
            <div className={styles.empty}>暂无服务</div>
          )}
        </div>
      </div>

      {/* 热门商品 */}
      <div className={styles.section}>
        <div className={styles.sectionHeader}>
          <h2 className={styles.sectionTitle}>
            <FireOutlined /> 热门商品
          </h2>
          <Link to="/categories?sort=hot" className={styles.moreLink}>
            查看更多 <RightOutlined />
          </Link>
        </div>
        <div className={styles.productGrid}>
          {hotProducts.map((product: any) => {
            const images = parseJsonArray(product.images);
            const location =
              product.location?.split(' - ')[1] || product.location || '';
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
                <span className={styles.productCondition}>
                  {product.condition}
                </span>
                <div className={styles.productInfo}>
                  <h3 className={styles.productTitle}>{product.title}</h3>
                  <div className={styles.productPrice}>
                    <span className={styles.currentPrice}>
                      ¥{product.price}
                    </span>
                    {product.originalPrice && (
                      <span className={styles.originalPrice}>
                        ¥{product.originalPrice}
                      </span>
                    )}
                  </div>
                  <div className={styles.productMeta}>
                    <span className={styles.productLocation}>
                      <ClockCircleOutlined /> {location}
                    </span>
                    <span className={styles.productViews}>
                      {product.viewCount || 0}浏览
                    </span>
                  </div>
                </div>
              </Link>
            );
          })}
          {hotProducts.length === 0 && !loading && (
            <div className={styles.empty}>暂无商品</div>
          )}
        </div>
      </div>

      {/* 最新发布 */}
      <div className={styles.section}>
        <div className={styles.sectionHeader}>
          <h2 className={styles.sectionTitle}>
            <ClockCircleOutlined /> 最新发布
          </h2>
          <Link to="/categories?sort=newest" className={styles.moreLink}>
            查看更多 <RightOutlined />
          </Link>
        </div>
        <div className={styles.productGrid}>
          {latestProducts.map((product: any) => {
            const images = parseJsonArray(product.images);
            const location =
              product.location?.split(' - ')[1] || product.location || '';
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
                <span className={styles.productCondition}>
                  {product.condition}
                </span>
                <div className={styles.productInfo}>
                  <h3 className={styles.productTitle}>{product.title}</h3>
                  <div className={styles.productPrice}>
                    <span className={styles.currentPrice}>
                      ¥{product.price}
                    </span>
                    {product.originalPrice && (
                      <span className={styles.originalPrice}>
                        ¥{product.originalPrice}
                      </span>
                    )}
                  </div>
                  <div className={styles.productMeta}>
                    <span className={styles.productLocation}>
                      <ClockCircleOutlined /> {location}
                    </span>
                    <span className={styles.productViews}>
                      {product.viewCount || 0}浏览
                    </span>
                  </div>
                </div>
              </Link>
            );
          })}
          {latestProducts.length === 0 && !loading && (
            <div className={styles.empty}>暂无商品</div>
          )}
        </div>
      </div>

      {/* 快捷发布入口 */}
      <div className={styles.publishBanner}>
        <div className={styles.bannerContent}>
          <h3>想要出手闲置物品/服务？</h3>
          <p>轻松发布，即刻开始交易</p>
          <Link to="/publish">
            <Button type="primary" size="large" className={styles.publishBtn}>
              立即发布
            </Button>
          </Link>
        </div>
      </div>
    </div>
  );
};

const CategoryIcon: React.FC<{ type: string }> = ({ type }) => {
  const iconMap: Record<string, React.ReactNode> = {
    book: <BookOutlined />,
    laptop: <LaptopOutlined />,
    'shopping-cart': <ShoppingCartOutlined />,
    skin: <SkinOutlined />,
    cloud: <CloudOutlined />,
    team: <TeamOutlined />,
    appstore: <MoreOutlined />,
  };
  return iconMap[type] || <AppstoreOutlined />;
};

export default HomePage;
