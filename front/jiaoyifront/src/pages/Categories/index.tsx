import ProductImage from '@/components/ProductImage';
import {
  CONDITION_OPTIONS,
  PRICE_RANGES,
  PRODUCT_CATEGORIES,
} from '@/constants/campus';
import { CategoryType, getProducts, Product } from '@/utils/api';
import {
  AppstoreOutlined,
  BarsOutlined,
  BookOutlined,
  CloudOutlined,
  FilterOutlined,
  LaptopOutlined,
  MoreOutlined,
  SearchOutlined,
  ShoppingCartOutlined,
  SkinOutlined,
  SortAscendingOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import { Link, useSearchParams } from '@umijs/max';
import { Button, Empty, Input, Select, Spin } from 'antd';
import { useEffect, useState } from 'react';
import styles from './index.less';

const { Search } = Input;

const CategoriesPage: React.FC = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const [loading, setLoading] = useState(false);
  const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid');
  const [selectedCategory, setSelectedCategory] = useState<
    CategoryType | '全部'
  >((searchParams.get('category') as CategoryType) || '全部');
  const [priceRange, setPriceRange] = useState<string>('');
  const [condition, setCondition] = useState<string>('');
  const [sortBy, setSortBy] = useState<string>('newest');
  const [searchText, setSearchText] = useState(
    searchParams.get('search') || '',
  );
  const [products, setProducts] = useState<Product[]>([]);
  const [total, setTotal] = useState(0);

  useEffect(() => {
    loadProducts();
  }, [selectedCategory, priceRange, condition, sortBy, searchText]);

  // 监听 URL search 参数变化，同步到搜索框
  useEffect(() => {
    const urlSearch = searchParams.get('search') || '';
    setSearchText(urlSearch);
  }, [searchParams]);

  const parseJsonArray = (val: any): string[] => {
    if (!val) return [];
    if (Array.isArray(val)) return val;
    try {
      return JSON.parse(val);
    } catch {
      return [];
    }
  };

  const loadProducts = async () => {
    setLoading(true);
    try {
      const categoryObj = PRODUCT_CATEGORIES.find(
        (c) => c.name === selectedCategory,
      );
      const categoryId = categoryObj?.id || '';

      let minPrice: number | undefined;
      let maxPrice: number | undefined;
      if (priceRange) {
        const parts = priceRange.split('-');
        minPrice = Number(parts[0]) || undefined;
        if (parts[1] && parts[1] !== '+') {
          maxPrice = Number(parts[1]) || undefined;
        } else if (parts[1] === '+') {
          maxPrice = undefined;
        }
      }

      const res = await getProducts({
        keyword: searchText || undefined,
        category: categoryId || undefined,
        condition: condition || undefined,
        minPrice,
        maxPrice,
        sortBy: sortBy === 'default' ? undefined : sortBy,
        page: 1,
        pageSize: 20,
      });

      if (res.code === 200 && res.data) {
        const list = Array.isArray(res.data) ? res.data : res.data.list || [];
        const parsedProducts = list.map((p: any) => ({
          ...p,
          images: parseJsonArray(p.images),
          tags: parseJsonArray(p.tags),
        }));
        setProducts(parsedProducts);
        setTotal(
          Array.isArray(res.data) ? (res.total ?? list.length) : (res.data.total ?? 0),
        );
      }
    } catch (error) {
      console.error('加载商品失败', error);
    } finally {
      setLoading(false);
    }
  };

  const handleSearch = (value: string) => {
    if (value.trim()) {
      searchParams.set('search', value.trim());
    } else {
      searchParams.delete('search');
    }
    setSearchParams(searchParams);
  };

  const handleCategoryChange = (category: string) => {
    setSelectedCategory(category as CategoryType | '全部');
    if (category === '全部') {
      searchParams.delete('category');
    } else {
      searchParams.set('category', category);
    }
    setSearchParams(searchParams);
  };

  const getCategoryIcon = (iconType: string) => {
    const iconMap: Record<string, React.ReactNode> = {
      book: <BookOutlined />,
      laptop: <LaptopOutlined />,
      'shopping-cart': <ShoppingCartOutlined />,
      skin: <SkinOutlined />,
      cloud: <CloudOutlined />,
      team: <TeamOutlined />,
      appstore: <MoreOutlined />,
    };
    return iconMap[iconType] || <AppstoreOutlined />;
  };

  return (
    <div className={styles.container}>
      {/* 侧边栏 */}
      <div className={styles.sidebar}>
        <div className={styles.searchBox}>
          <Search
            placeholder="搜索商品..."
            allowClear
            value={searchText}
            onChange={(e) => setSearchText(e.target.value)}
            onSearch={handleSearch}
            prefix={<SearchOutlined />}
          />
        </div>

        <div className={styles.filterSection}>
          <h3 className={styles.filterTitle}>
            <AppstoreOutlined /> 分类
          </h3>
          <div className={styles.categoryList}>
            <div
              className={`${styles.categoryItem} ${
                selectedCategory === '全部' ? styles.active : ''
              }`}
              onClick={() => handleCategoryChange('全部')}
            >
              <span>全部商品</span>
            </div>
            {PRODUCT_CATEGORIES.map((cat) => (
              <div
                key={cat.id}
                className={`${styles.categoryItem} ${
                  selectedCategory === cat.name ? styles.active : ''
                }`}
                onClick={() => handleCategoryChange(cat.name)}
                style={{ '--category-color': cat.color } as React.CSSProperties}
              >
                <span className={styles.categoryIcon}>
                  {getCategoryIcon(cat.icon)}
                </span>
                <span>{cat.name}</span>
              </div>
            ))}
          </div>
        </div>

        <div className={styles.filterSection}>
          <h3 className={styles.filterTitle}>
            <FilterOutlined /> 价格区间
          </h3>
          <Select
            placeholder="选择价格"
            allowClear
            style={{ width: '100%' }}
            value={priceRange || undefined}
            onChange={setPriceRange}
            options={PRICE_RANGES.map((p) => ({
              label: p.label,
              value: p.value,
            }))}
          />
        </div>

        <div className={styles.filterSection}>
          <h3 className={styles.filterTitle}>成色</h3>
          <Select
            placeholder="选择成色"
            allowClear
            style={{ width: '100%' }}
            value={condition || undefined}
            onChange={setCondition}
            options={CONDITION_OPTIONS.map((c) => ({
              label: c.label,
              value: c.value,
            }))}
          />
        </div>

        <Button
          block
          onClick={() => {
            setSelectedCategory('全部');
            setPriceRange('');
            setCondition('');
            setSortBy('default');
            setSearchText('');
          }}
        >
          重置筛选
        </Button>
      </div>

      {/* 主内容区 */}
      <div className={styles.mainContent}>
        <div className={styles.toolbar}>
          <div className={styles.resultCount}>
            共找到 <span className={styles.count}>{total}</span> 件商品
          </div>
          <div className={styles.toolbarRight}>
            <Select
              value={sortBy}
              onChange={setSortBy}
              style={{ width: 140 }}
              options={[
                { label: '默认排序', value: 'default' },
                { label: '价格从低到高', value: 'price_asc' },
                { label: '价格从高到低', value: 'price_desc' },
                { label: '热门优先', value: 'hot' },
                { label: '最新发布', value: 'newest' },
              ]}
              prefix={<SortAscendingOutlined />}
            />
            <div className={styles.viewToggle}>
              <BarsOutlined
                className={viewMode === 'list' ? styles.active : ''}
                onClick={() => setViewMode('list')}
              />
              <AppstoreOutlined
                className={viewMode === 'grid' ? styles.active : ''}
                onClick={() => setViewMode('grid')}
              />
            </div>
          </div>
        </div>

        <Spin spinning={loading}>
          {products.length === 0 ? (
            <Empty description="暂无商品" />
          ) : (
            <div
              className={
                viewMode === 'grid' ? styles.productGrid : styles.productList
              }
            >
              {products.map((product: any) => {
                const images = parseJsonArray(product.images);
                const location =
                  product.location?.split(' - ')[1] || product.location || '';
                return (
                  <Link
                    key={product.id}
                    to={`/product/${product.id}`}
                    className={
                      viewMode === 'grid'
                        ? styles.productCardGrid
                        : styles.productCardList
                    }
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
                      <p className={styles.productDesc}>
                        {product.description}
                      </p>
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
                          {location}
                        </span>
                      </div>
                      <div className={styles.productStats}>
                        <span>浏览 {product.viewCount || 0}</span>
                        <span>收藏 {product.favoriteCount || 0}</span>
                      </div>
                    </div>
                  </Link>
                );
              })}
            </div>
          )}
        </Spin>
      </div>
    </div>
  );
};

export default CategoriesPage;
