import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuthStore } from '../../stores/authStore';

/**
 * 管理员路由守卫：非管理员直接回控制台首页。
 * 仅做前端兜底，接口侧仍会校验角色。
 */
export default function RequireAdmin({ children }: { children: ReactNode }) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const admin = useAuthStore((s) => s.capabilities?.admin ?? false);

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
  if (!admin) {
    return <Navigate to="/admin/agents" replace />;
  }
  return <>{children}</>;
}
