// 全局共享数据
import { useState, useCallback } from 'react';
import { getCurrentUser } from '@/utils/api';
import type { User } from '@/utils/api';

export interface GlobalState {
  currentUser?: User;
}

const useGlobal = () => {
  const [currentUser, setCurrentUser] = useState<User | undefined>(undefined);

  const refreshCurrentUser = useCallback(async () => {
    try {
      const res = await getCurrentUser();
      if (res.code === 200 && res.data) {
        setCurrentUser(res.data);
        localStorage.setItem('userInfo', JSON.stringify(res.data));
        return res.data;
      }
    } catch (e) {
      // ignore
    }
    return undefined;
  }, []);

  return {
    currentUser,
    setCurrentUser,
    refreshCurrentUser,
  };
};

export default useGlobal;
