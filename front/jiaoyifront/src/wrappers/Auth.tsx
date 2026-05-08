/**
 * 路由守卫 - 检查用户是否登录
 */
import { getToken } from '@/utils/useUser';
import { Navigate } from '@umijs/max';

const AuthWrapper: React.FC<{ children: React.ReactNode }> = (props) => {
  const token = getToken();

  if (!token) {
    // 未登录，跳转到登录页
    return <Navigate to="/login" replace />;
  }

  // 已登录，渲染子组件
  return <>{props.children}</>;
};

export default AuthWrapper;
