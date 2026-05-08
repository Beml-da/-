/**
 * 用户状态管理 Hook
 */
import { useState } from 'react';

export interface UserInfo {
  id: number;
  username: string;
  nickname: string;
  phone?: string;
  email?: string;
  avatar?: string;
  gender?: 'male' | 'female' | 'secret';
  bio?: string;
  verified?: boolean;
}

const TOKEN_KEY = 'token';
const USER_INFO_KEY = 'userInfo';

export function useUser() {
  const [user, setUser] = useState<UserInfo | null>(() => {
    const stored = localStorage.getItem(USER_INFO_KEY);
    return stored ? JSON.parse(stored) : null;
  });
  const [token, setToken] = useState<string | null>(() => {
    return localStorage.getItem(TOKEN_KEY);
  });
  const [loading, setLoading] = useState(false);

  // 保存登录信息
  const saveLoginInfo = (token: string, userInfo: UserInfo) => {
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(USER_INFO_KEY, JSON.stringify(userInfo));
    setToken(token);
    setUser(userInfo);
  };

  // 清除登录信息
  const clearLoginInfo = () => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_INFO_KEY);
    setToken(null);
    setUser(null);
  };

  // 检查是否已登录
  const isLoggedIn = () => {
    return !!token && !!user;
  };

  return {
    user,
    token,
    loading,
    setLoading,
    saveLoginInfo,
    clearLoginInfo,
    isLoggedIn,
  };
}

// 便捷方法
export const getToken = () => localStorage.getItem(TOKEN_KEY);
export const getUserInfo = (): UserInfo | null => {
  const stored = localStorage.getItem(USER_INFO_KEY);
  return stored ? JSON.parse(stored) : null;
};
export const setToken = (token: string) =>
  localStorage.setItem(TOKEN_KEY, token);
export const setUserInfo = (user: UserInfo) =>
  localStorage.setItem(USER_INFO_KEY, JSON.stringify(user));
export const saveUserInfo = (user: UserInfo) =>
  localStorage.setItem(USER_INFO_KEY, JSON.stringify(user));
export const removeToken = () => localStorage.removeItem(TOKEN_KEY);
export const removeUserInfo = () => localStorage.removeItem(USER_INFO_KEY);

// 登录信息管理（独立函数，用于组件外部调用）
export const saveLoginInfo = (token: string, userInfo: UserInfo) => {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(USER_INFO_KEY, JSON.stringify(userInfo));
};

export const clearLoginInfo = () => {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_INFO_KEY);
};
