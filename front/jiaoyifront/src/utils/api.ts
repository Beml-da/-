/**
 * 校园交易平台 API
 */
import request from './request';

// ==================== 响应类型 ====================
export interface ApiResponse<T = any> {
  code: number;
  message: string;
  data: T;
  total?: number;
  page?: number;
  pageSize?: number;
}

// ==================== 认证相关 ====================
export interface LoginParams {
  username: string;
  password: string;
}

export interface SmsLoginParams {
  phone: string;
  code: string;
}

export interface LoginResponse {
  token: string;
  user: User;
}

export interface User {
  id: number;
  username: string;
  nickname?: string;
  avatar?: string;
  phone?: string;
  email?: string;
  school?: string;
  studentId?: string;
  creditScore?: number;
  level?: number;
  isVerified?: boolean;
  status?: number;
  createTime?: string;
}

/**
 * 用户名密码登录
 */
export async function loginByPassword(
  params: LoginParams,
): Promise<ApiResponse<LoginResponse>> {
  return request('/api/auth/login', {
    method: 'POST',
    data: params,
  });
}

/**
 * 注册
 */
export async function register(params: {
  username: string;
  password: string;
  nickname?: string;
}): Promise<ApiResponse<LoginResponse>> {
  return request('/api/auth/register', {
    method: 'POST',
    data: params,
  });
}

/**
 * 手机验证码登录
 */
export async function loginBySms(
  params: SmsLoginParams,
): Promise<ApiResponse<LoginResponse>> {
  return request('/api/auth/login/sms', {
    method: 'POST',
    data: params,
  });
}

/**
 * 退出登录
 */
export async function logout(): Promise<ApiResponse<null>> {
  return request('/api/auth/logout', {
    method: 'POST',
  });
}

/**
 * 获取当前用户信息
 */
export async function getCurrentUser(): Promise<ApiResponse<User>> {
  return request('/api/auth/current', {
    method: 'GET',
  });
}

/**
 * 更新个人资料
 */
export async function updateProfile(
  params: Partial<User>,
): Promise<ApiResponse<User>> {
  return request('/api/auth/profile', {
    method: 'PUT',
    data: params,
  });
}

// ==================== 商品相关 ====================
export interface Product {
  id: number;
  title: string;
  description: string;
  price: number;
  originalPrice?: number;
  images?: string[];
  categoryId: string;
  categoryName?: string;
  subCategory?: string;
  condition?: '全新' | '几乎全新' | '轻微使用' | '正常使用' | '较旧';
  status: '在售' | '已售出' | '已下架';
  viewCount?: number;
  favoriteCount?: number;
  sellerId: number;
  seller?: User;
  location?: string;
  isNegotiable?: boolean;
  tags?: string[];
  isFavorited?: boolean;
  createTime?: string;
  type?: 'product' | 'service';
  serviceType?: string;
  priceUnit?: string;
  rating?: number;
  ratingCount?: number;
  orderCount?: number;
}

export interface ProductListResponse {
  list: Product[];
  total: number;
  page: number;
  pageSize: number;
}

export interface ProductQuery {
  keyword?: string;
  category?: string;
  condition?: string;
  minPrice?: number;
  maxPrice?: number;
  sortBy?: 'default' | 'price_asc' | 'price_desc' | 'hot' | 'newest';
  page?: number;
  pageSize?: number;
}

/**
 * 获取商品列表
 */
export async function getProducts(
  params?: ProductQuery,
): Promise<ApiResponse<ProductListResponse>> {
  return request('/api/products', {
    method: 'GET',
    params,
  });
}

/**
 * 获取商品详情
 */
export async function getProduct(id: number): Promise<ApiResponse<Product>> {
  return request(`/api/products/${id}`, {
    method: 'GET',
  });
}

/**
 * 获取热门商品
 */
export async function getHotProducts(
  limit?: number,
): Promise<ApiResponse<Product[]>> {
  return request('/api/products/hot', {
    method: 'GET',
    params: { limit },
  });
}

/**
 * 统一搜索（商品+服务）
 */
export async function searchAll(params: {
  keyword: string;
  sortBy?: 'default' | 'price_asc' | 'price_desc' | 'hot' | 'newest';
  page?: number;
  pageSize?: number;
}): Promise<ApiResponse<any>> {
  return request('/api/products/search', {
    method: 'GET',
    params,
  });
}

/**
 * 搜索建议（按名称去重）
 */
export interface SearchSuggestion {
  id: number;
  title: string;
  price: number;
  coverImage?: string;
}

export async function getSearchSuggestions(
  keyword: string,
  limit?: number,
): Promise<ApiResponse<SearchSuggestion[]>> {
  return request('/api/products/suggestions', {
    method: 'GET',
    params: { keyword, limit },
  });
}

/**
 * 获取最新商品
 */
export async function getNewProducts(
  limit?: number,
): Promise<ApiResponse<Product[]>> {
  return request('/api/products/newest', {
    method: 'GET',
    params: { limit },
  });
}

/**
 * 获取最新商品（用于首页最新发布区块）
 */
export async function getNewestProducts(
  limit?: number,
): Promise<ApiResponse<Product[]>> {
  return request('/api/products/newest-all', {
    method: 'GET',
    params: { limit },
  });
}

/**
 * 获取卖家商品
 */
export async function getSellerProducts(
  sellerId: number,
): Promise<ApiResponse<Product[]>> {
  return request(`/api/products/seller/${sellerId}`, {
    method: 'GET',
  });
}

/**
 * 发布商品
 */
export async function createProduct(
  params: Partial<Product>,
): Promise<ApiResponse<Product>> {
  console.log('[createProduct] 收到 params:', JSON.stringify(params));
  return request('/api/products', {
    method: 'POST',
    data: params,
  });
}

/**
 * 更新商品
 */
export async function updateProduct(
  id: number,
  params: Partial<Product>,
): Promise<ApiResponse<Product>> {
  return request(`/api/products/${id}`, {
    method: 'PUT',
    data: params,
  });
}

/**
 * 更新商品状态
 */
export async function updateProductStatus(
  id: number,
  status: string,
): Promise<ApiResponse<null>> {
  return request(`/api/products/${id}/status`, {
    method: 'PUT',
    data: { status },
  });
}

/**
 * 删除商品
 */
export async function deleteProduct(id: number): Promise<ApiResponse<null>> {
  return request(`/api/products/${id}`, {
    method: 'DELETE',
  });
}

/**
 * 获取我的发布
 */
export async function getMyProducts(): Promise<ApiResponse<Product[]>> {
  return request('/api/products/my', {
    method: 'GET',
  });
}

// ==================== 服务相关 ====================
export interface Service {
  id: number;
  title: string;
  description: string;
  price: number;
  priceUnit?: string;
  serviceType: string;
  status: '可用' | '暂停' | '已结束';
  providerId: number;
  provider?: User;
  location?: string;
  tags: string[];
  rating: number;
  ratingCount: number;
  orderCount: number;
  viewCount: number;
  favoriteCount: number;
  createTime?: string;
}

/**
 * 获取服务列表
 */
export async function getServices(params?: {
  keyword?: string;
  serviceType?: string;
  page?: number;
  pageSize?: number;
}): Promise<ApiResponse<Service[]>> {
  return request('/api/services', {
    method: 'GET',
    params,
  });
}

/**
 * 获取服务详情
 */
export async function getService(id: number): Promise<ApiResponse<Service>> {
  return request(`/api/services/${id}`, {
    method: 'GET',
  });
}

/**
 * 获取我的服务
 */
export async function getMyServices(): Promise<ApiResponse<Service[]>> {
  return request('/api/services/my', {
    method: 'GET',
  });
}

/**
 * 发布服务
 */
export async function createService(
  params: Partial<Service>,
): Promise<ApiResponse<Service>> {
  console.log('[createService] 收到 params:', JSON.stringify(params));
  return request('/api/services', {
    method: 'POST',
    data: params,
  });
}

/**
 * 更新服务状态
 */
export async function updateServiceStatus(
  id: number,
  status: string,
): Promise<ApiResponse<null>> {
  return request(`/api/services/${id}/status`, {
    method: 'PUT',
    data: { status },
  });
}

/**
 * 更新服务
 */
export async function updateService(
  id: number,
  params: Partial<Service>,
): Promise<ApiResponse<Service>> {
  return request(`/api/services/${id}`, {
    method: 'PUT',
    data: params,
  });
}

/**
 * 删除服务
 */
export async function deleteService(id: number): Promise<ApiResponse<null>> {
  return request(`/api/services/${id}`, {
    method: 'DELETE',
  });
}

// ==================== 订单相关 ====================
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
  buyer?: User;
  seller?: User;
  price: number;
  quantity: number;
  totalAmount: number;
  status:
    | '待付款'
    | '待发货'
    | '待收货'
    | '已完成'
    | '已取消'
    | '退款中'
    | '已退款';
  contact?: string;
  remark?: string;
  createTime?: string;
  updateTime?: string;
  completedTime?: string;
}

export interface OrderQuery {
  status?: string;
  type?: string;
  role?: 'buy' | 'sell';
  page?: number;
  pageSize?: number;
}

/**
 * 订单列表响应
 */
export interface OrderListResponse {
  list: Order[];
  total: number;
  page: number;
  pageSize: number;
}

/**
 * 获取订单列表
 */
export async function getOrders(
  params?: OrderQuery,
): Promise<ApiResponse<OrderListResponse>> {
  return request('/api/orders', {
    method: 'GET',
    params,
  });
}

/**
 * 获取订单详情
 */
export async function getOrder(id: number): Promise<ApiResponse<Order>> {
  return request(`/api/orders/${id}`, {
    method: 'GET',
  });
}

/**
 * 创建订单
 */
export async function createOrder(
  params: Partial<Order>,
): Promise<ApiResponse<Order>> {
  return request('/api/orders', {
    method: 'POST',
    data: params,
  });
}

/**
 * 更新订单状态
 */
export async function updateOrderStatus(
  id: number,
  status: string,
): Promise<ApiResponse<null>> {
  return request(`/api/orders/${id}/status`, {
    method: 'PUT',
    data: { status },
  });
}

/**
 * 取消订单
 */
export async function cancelOrder(
  id: number,
  reason?: string,
): Promise<ApiResponse<null>> {
  return request(`/api/orders/${id}/cancel`, {
    method: 'PUT',
    data: { reason },
  });
}

// ==================== 分类相关 ====================
export interface Category {
  id: string;
  name: string;
  icon?: string;
  color?: string;
  isService?: boolean;
}

/**
 * 获取所有分类
 */
export async function getCategories(): Promise<ApiResponse<Category[]>> {
  return request('/api/categories', {
    method: 'GET',
  });
}

/**
 * 获取商品分类
 */
export async function getProductCategories(): Promise<ApiResponse<Category[]>> {
  return request('/api/categories/products', {
    method: 'GET',
  });
}

/**
 * 获取服务分类
 */
export async function getServiceCategories(): Promise<ApiResponse<Category[]>> {
  return request('/api/categories/services', {
    method: 'GET',
  });
}

// ==================== 收藏相关 ====================
export interface Favorite {
  id: number;
  userId: number;
  productId: number;
  product?: Product;
  createTime?: string;
}

/**
 * 获取收藏列表
 */
export async function getFavorites(): Promise<ApiResponse<Favorite[]>> {
  return request('/api/favorites', {
    method: 'GET',
  });
}

/**
 * 添加收藏
 */
export async function addFavorite(
  productId: number,
): Promise<ApiResponse<null>> {
  return request('/api/favorites', {
    method: 'POST',
    data: { productId },
  });
}

/**
 * 取消收藏
 */
export async function removeFavorite(
  productId: number,
): Promise<ApiResponse<null>> {
  return request(`/api/favorites/${productId}`, {
    method: 'DELETE',
  });
}

/**
 * 检查是否收藏
 */
export async function checkFavorite(
  productId: number,
): Promise<ApiResponse<boolean>> {
  return request(`/api/favorites/check/${productId}`, {
    method: 'GET',
  });
}

// ==================== 消息相关 ====================
export interface ChatSession {
  id: number;
  userId: number;
  targetUserId: number;
  targetUser?: User;
  lastMessage?: Message;
  unreadCount: number;
  createTime?: string;
  updateTime?: string;
}

export interface Message {
  id: number;
  sessionId: number;
  type: 'chat' | 'system' | 'order';
  fromId: number;
  toId: number;
  fromUser?: User;
  toUser?: User;
  content: string;
  isRead: boolean;
  relatedType?: string;
  relatedId?: number;
  createTime?: string;
}

/**
 * 获取会话列表
 */
export async function getChatSessions(): Promise<ApiResponse<ChatSession[]>> {
  return request('/api/messages/sessions', {
    method: 'GET',
  });
}

/**
 * 获取或创建会话
 */
export async function getOrCreateSession(
  targetUserId: number,
): Promise<ApiResponse<ChatSession>> {
  return request(`/api/messages/sessions/${targetUserId}`, {
    method: 'GET',
  });
}

/**
 * 获取消息列表
 */
export async function getMessages(
  sessionId: number,
  page?: number,
  pageSize?: number,
): Promise<ApiResponse<{ list: Message[]; total: number }>> {
  return request(`/api/messages/sessions/${sessionId}/messages`, {
    method: 'GET',
    params: { page, pageSize },
  });
}

/**
 * 发送消息
 */
export async function sendMessage(
  sessionId: number,
  content: string,
  type?: string,
  relatedType?: string,
  relatedId?: number,
): Promise<ApiResponse<Message>> {
  return request(`/api/messages/sessions/${sessionId}`, {
    method: 'POST',
    data: { content, type, relatedType, relatedId },
  });
}

/**
 * 标记消息已读
 */
export async function markMessagesAsRead(
  sessionId: number,
): Promise<ApiResponse<null>> {
  return request(`/api/messages/sessions/${sessionId}/read`, {
    method: 'PUT',
  });
}

/**
 * 删除会话
 */
export async function deleteSession(
  sessionId: number,
): Promise<ApiResponse<null>> {
  return request(`/api/messages/sessions/${sessionId}`, {
    method: 'DELETE',
  });
}

/**
 * 获取未读消息数
 */
export async function getUnreadCount(): Promise<
  ApiResponse<{ count: number }>
> {
  return request('/api/messages/unread/count', {
    method: 'GET',
  });
}

// ==================== 通知相关 ====================
export interface Notification {
  id: number;
  userId: number;
  type: 'order' | 'system' | 'activity';
  title: string;
  content: string;
  isRead: boolean;
  link?: string;
  createTime?: string;
}

/**
 * 获取通知列表
 */
export async function getNotifications(
  page?: number,
  pageSize?: number,
): Promise<ApiResponse<{ list: Notification[]; total: number }>> {
  return request('/api/notifications', {
    method: 'GET',
    params: { page, pageSize },
  });
}

/**
 * 获取未读通知数
 */
export async function getNotificationUnreadCount(): Promise<
  ApiResponse<{ count: number }>
> {
  return request('/api/notifications/unread/count', {
    method: 'GET',
  });
}

/**
 * 标记通知已读
 */
export async function markNotificationAsRead(
  id: number,
): Promise<ApiResponse<null>> {
  return request(`/api/notifications/${id}/read`, {
    method: 'PUT',
  });
}

/**
 * 全部标记已读
 */
export async function markAllNotificationsAsRead(): Promise<ApiResponse<null>> {
  return request('/api/notifications/read/all', {
    method: 'PUT',
  });
}
