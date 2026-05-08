import { Category } from '@/types';

// 商品分类配置
export const PRODUCT_CATEGORIES: Category[] = [
  {
    id: 'books',
    name: '书籍教材',
    icon: 'book',
    color: '#1890ff',
    subCategories: [
      '专业教材',
      '考研资料',
      '四六级资料',
      '小说传记',
      '杂志期刊',
      '其他书籍',
    ],
  },
  {
    id: 'electronics',
    name: '电子产品',
    icon: 'laptop',
    color: '#722ed1',
    subCategories: [
      '手机平板',
      '电脑配件',
      '耳机音响',
      '充电设备',
      '智能穿戴',
      '其他电子',
    ],
  },
  {
    id: 'daily',
    name: '日常用品',
    icon: 'shopping-cart',
    color: '#52c41a',
    subCategories: [
      '收纳整理',
      '清洁用品',
      '生活电器',
      '厨房用品',
      '家居装饰',
      '其他用品',
    ],
  },
  {
    id: 'fashion',
    name: '衣物鞋包',
    icon: 'skin',
    color: '#fa8c16',
    subCategories: ['春夏装', '秋冬装', '鞋类', '箱包', '配饰', '其他'],
  },
  {
    id: 'virtual',
    name: '虚拟物品',
    icon: 'cloud',
    color: '#13c2c2',
    subCategories: [
      '课程资料',
      '软件账号',
      '游戏装备',
      '优惠券',
      '会员卡',
      '其他',
    ],
  },
  {
    id: 'service',
    name: '校园服务',
    icon: 'team',
    color: '#eb2f96',
    subCategories: ['取快递', '带外卖', '代买', '打印', '跑腿', '其他'],
  },
  {
    id: 'other',
    name: '其他',
    icon: 'appstore',
    color: '#8c8c8c',
    subCategories: ['文具', '体育用品', '乐器', '宠物用品', '其他'],
  },
];

// 服务类型配置
export const SERVICE_TYPES = [
  { value: '取快递', label: '取快递', icon: 'inbox', color: '#1890ff' },
  { value: '带外卖', label: '带外卖', icon: 'coffee', color: '#fa8c16' },
  { value: '代买', label: '代买', icon: 'shopping', color: '#52c41a' },
  { value: '打印', label: '打印', icon: 'printer', color: '#722ed1' },
  { value: '跑腿', label: '跑腿', icon: 'rocket', color: '#eb2f96' },
  { value: '其他', label: '其他', icon: 'ellipsis', color: '#8c8c8c' },
];

// 商品成色选项
export const CONDITION_OPTIONS = [
  { value: '全新', label: '全新', color: '#52c41a' },
  { value: '几乎全新', label: '几乎全新', color: '#1890ff' },
  { value: '轻微使用', label: '轻微使用', color: '#faad14' },
  { value: '正常使用', label: '正常使用', color: '#fa8c16' },
  { value: '较旧', label: '较旧', color: '#8c8c8c' },
];

// 订单状态配置
export const ORDER_STATUS_CONFIG = {
  待售: { color: '#1890ff', icon: 'shop' },
  待付款: { color: '#faad14', icon: 'clock-circle' },
  待发货: { color: '#1890ff', icon: 'inbox' },
  待收货: { color: '#722ed1', icon: 'car' },
  已完成: { color: '#52c41a', icon: 'check-circle' },
  已取消: { color: '#8c8c8c', icon: 'close-circle' },
  退款中: { color: '#fa8c16', icon: 'exclamation-circle' },
  已退款: { color: '#ff4d4f', icon: 'rollback' },
};

// 热门搜索词
export const HOT_SEARCH_KEYWORDS = [
  '高等数学',
  '线性代数',
  '考研资料',
  '四六级',
  'MacBook',
  'iPad',
  '自行车',
  '台灯',
  '耳机',
  '充电宝',
  '取快递',
  '带外卖',
  '打印资料',
];

// 校园位置示例
export const CAMPUS_LOCATIONS = [
  '校内 - 学生宿舍区',
  '校内 - 教学楼区',
  '校内 - 食堂附近',
  '校内 - 图书馆区',
  '校内 - 体育馆区',
  '校内 - 行政楼区',
  '校外 - 学校周边',
];

// 页面尺寸配置
export const PAGE_SIZE = 20;

// 价格区间
export const PRICE_RANGES = [
  { label: '0-50元', value: '0-50' },
  { label: '50-100元', value: '50-100' },
  { label: '100-300元', value: '100-300' },
  { label: '300-500元', value: '300-500' },
  { label: '500元以上', value: '500+' },
];

// 消息类型
export const MESSAGE_TYPES = {
  chat: { label: '私信', color: '#1890ff' },
  system: { label: '系统通知', color: '#52c41a' },
  order: { label: '订单消息', color: '#fa8c16' },
};
