/**
 * 全局请求配置
 *  - 请求拦截器：从 localStorage 读取 token，加到 Authorization 头
 *  - 响应拦截器：统一处理 401/403 -> 清理本地态并跳登录
 *
 * 调试日志只在 development 模式下输出，避免生产控制台噪音。
 */
import { history } from '@umijs/max';
import { message } from 'antd';
import { extend } from 'umi-request';

const isDev = process.env.NODE_ENV === 'development';

const request = extend({
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// 请求拦截器 - 添加 Token
request.interceptors.request.use((url, options) => {
  if (isDev) {
    console.log(
      '[请求] url:',
      url,
      '| method:',
      options?.method,
      '| data:',
      JSON.stringify(options?.data),
    );
  }
  const token = localStorage.getItem('token');
  if (token) {
    options.headers = {
      ...options.headers,
      Authorization: `Bearer ${token}`,
    };
  }
  return { url, options };
});

// 响应拦截器 - 处理错误
request.interceptors.response.use(
  async (response) => {
    const data = await response.clone().json();
    if (isDev) {
      console.log('[响应]', response.url, '|', JSON.stringify(data));
    }

    // 如果是未授权，跳转登录
    if (data.code === 401 || data.code === 403) {
      message.error(data.message || '登录已过期，请重新登录');
      localStorage.removeItem('token');
      localStorage.removeItem('userInfo');
      history.push('/login');
      return Promise.reject(data);
    }

    // 409 通常表示幂等冲突（如重复下单），保留 message 给业务层提示
    if (data.code === 409) {
      return Promise.reject(data);
    }

    return response;
  },
  (error) => {
    if (error.response) {
      message.error('网络请求失败，请检查网络连接');
    }
    return Promise.reject(error);
  },
);

export default request;