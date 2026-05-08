/**
 * 全局请求配置
 */
import { history } from '@umijs/max';
import { message } from 'antd';
import { extend } from 'umi-request';

const request = extend({
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// 请求拦截器 - 添加 Token
request.interceptors.request.use((url, options) => {
  console.log(
    '[请求拦截器] url:',
    url,
    '| options.data:',
    JSON.stringify(options?.data),
    '| options.method:',
    options?.method,
  );
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
    console.log(
      '[响应拦截器] url:',
      response.url,
      '| data:',
      JSON.stringify(data),
    );

    // 如果是未授权，跳转登录
    if (data.code === 401 || data.code === 403) {
      message.error(data.message || '登录已过期，请重新登录');
      localStorage.removeItem('token');
      localStorage.removeItem('userInfo');
      history.push('/login');
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
