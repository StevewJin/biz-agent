import { useMemo } from 'react';
import { useAuthStore } from '../stores/authStore';

/**
 * 账号权限的展示层封装。
 *
 * <p>只用于隐藏无权操作的入口；真正的越权拦截由后端接口完成。</p>
 */
export function useCapabilities() {
  const capabilities = useAuthStore((s) => s.capabilities);
  const userId = useAuthStore((s) => s.user?.id ?? null);

  return useMemo(() => {
    const admin = capabilities?.admin ?? false;
    const managedTeamIds = capabilities?.managedTeamIds ?? [];
    const teamIds = new Set((capabilities?.teams ?? []).map((t) => t.id));

    /** 是否是某个团队的管理者（负责人/团队管理员/系统管理员） */
    const canManageTeam = (teamId?: number | null) =>
      teamId != null && (admin || managedTeamIds.includes(teamId));

    /** 是否是某个团队的成员（可用于创建团队维度资源） */
    const inTeam = (teamId?: number | null) =>
      teamId == null || admin || teamIds.has(teamId);

    /** 是否可管理某条资源：系统管理员 / 归属人 / 团队管理者 */
    const canManage = (ownerId?: number | null, teamId?: number | null) =>
      admin || (ownerId != null && ownerId === userId) || canManageTeam(teamId);

    return {
      capabilities,
      userId,
      admin,
      managedTeamIds,
      canManageTeam,
      inTeam,
      canManage,
      permissions: capabilities?.permissions,
    };
  }, [capabilities, userId]);
}
