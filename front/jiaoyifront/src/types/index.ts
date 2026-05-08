// 校园交易平台类型定义

export interface User {
  id: number;
  username: string;
  nickname: string;
  avatar?: string;
  phone: string;
  email: string;
  school?: string;
  studentId?: string;
  creditScore: number; // 信用分
  level: number; // 用户等级
  isVerified: boolean; // 是否已认证
  createdAt: string;
}

export interface Product {
  id: number;
  title: string;
  description: string;
  price: number;
  originalPrice?: number; // 原价
  images: string[];
  category: CategoryType;
  subCategory?: string;
  condition: '全新' | '几乎全新' | '轻微使用' | '正常使用' | '较旧';
  status: ProductStatus;
  viewCount: number;
  favoriteCount: number;
  sellerId: number;
  seller: User;
  location: string;
  isNegotiable: boolean; // 可议价
  tags: string[];
  createdAt: string;
  updatedAt: string;
}

export type ProductStatus = '在售' | '已售出' | '已下架';

export interface Service {
  id: number;
  title: string;
  description: string;
  price: number;
  serviceType: ServiceType;
  status: '可用' | '暂停' | '已结束';
  providerId: number;
  provider: User;
  location: string;
  tags: string[];
  rating: number;
  orderCount: number;
  createdAt: string;
}

export type ServiceType =
  | '取快递'
  | '带外卖'
  | '代买'
  | '打印'
  | '跑腿'
  | '其他';

export type CategoryType =
  | '书籍教材'
  | '电子产品'
  | '日常用品'
  | '衣物鞋包'
  | '虚拟物品'
  | '校园服务'
  | '其他';

export interface Category {
  id: string;
  name: CategoryType;
  icon: string;
  color: string;
  subCategories: string[];
}

export interface Order {
  id: number;
  orderNo: string;
  type: '商品' | '服务';
  productId?: number;
  serviceId?: number;
  product?: Product;
  service?: Service;
  buyerId: number;
  sellerId: number;
  buyer: User;
  seller: User;
  price: number;
  quantity: number;
  totalAmount: number;
  status: OrderStatus;
  contact: string;
  remark?: string;
  createdAt: string;
  updatedAt: string;
  completedAt?: string;
}

export type OrderStatus =
  | '待付款'
  | '待发货'
  | '待收货'
  | '已完成'
  | '已取消'
  | '退款中'
  | '已退款';

export interface Message {
  id: number;
  type: 'chat' | 'system' | 'order';
  fromId: number;
  toId: number;
  fromUser?: User;
  toUser?: User;
  content: string;
  isRead: boolean;
  relatedId?: number; // 关联订单或商品ID
  createdAt: string;
}

export interface ChatSession {
  id: number;
  userId: number;
  targetUserId: number;
  targetUser: User;
  lastMessage?: Message;
  unreadCount: number;
  updatedAt: string;
}

export interface Favorite {
  id: number;
  userId: number;
  productId: number;
  product: Product;
  createdAt: string;
}

export interface Notification {
  id: number;
  type: 'order' | 'system' | 'activity';
  title: string;
  content: string;
  isRead: boolean;
  link?: string;
  createdAt: string;
}

// 表单数据类型
export interface PublishProductForm {
  title: string;
  description: string;
  price: number;
  category: CategoryType;
  subCategory?: string;
  condition: Product['condition'];
  images: string[];
  location: string;
  isNegotiable: boolean;
  tags: string[];
}

export interface PublishServiceForm {
  title: string;
  description: string;
  price: number;
  serviceType: ServiceType;
  location: string;
  tags: string[];
}
