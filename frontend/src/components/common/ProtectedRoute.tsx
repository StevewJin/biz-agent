import { Navigate } from 'react-router-dom';
import { useAuthStore } from '../../stores/authStore';

/**
 * 受保护路由 - 未登录用户重定向到登录页
 */
export default function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return <>{children}</>;
}
