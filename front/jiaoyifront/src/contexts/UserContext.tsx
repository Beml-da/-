import { createContext, useContext, useEffect, useState, ReactNode } from 'react';

interface User {
  id?: number;
  username?: string;
  nickname?: string;
  avatar?: string;
  phone?: string;
  email?: string;
  [key: string]: any;
}

interface UserContextType {
  user: User | null;
  updateUser: (user: User | null) => void;
}

export const UserContext = createContext<UserContextType>({
  user: null,
  updateUser: () => {},
});

export const useUserContext = () => useContext(UserContext);

interface UserProviderProps {
  children: ReactNode;
  initialUser?: User | null;
}

export const UserProvider: React.FC<UserProviderProps> = ({ children, initialUser }) => {
  const [user, setUser] = useState<User | null>(() => {
    const stored = localStorage.getItem('userInfo');
    return stored ? JSON.parse(stored) : initialUser ?? null;
  });

  useEffect(() => {
    const handleStorage = () => {
      const stored = localStorage.getItem('userInfo');
      setUser(stored ? JSON.parse(stored) : null);
    };
    window.addEventListener('user-login-updated', handleStorage);
    return () => window.removeEventListener('user-login-updated', handleStorage);
  }, []);

  return (
    <UserContext.Provider value={{ user, updateUser: setUser }}>
      {children}
    </UserContext.Provider>
  );
};
