import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import * as api from '../services/api';
import type { UserInfo } from '../types';
import type { Capabilities } from '../types/admin';

interface DataContext {
  type: 'personal' | 'team';
  teamId?: number;
  teamName?: string;
}

interface AuthState {
  token: string | null;
  refreshToken: string | null;
  user: UserInfo | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  dataContext: DataContext;
  /** 当前账号的能力清单（菜单/按钮收敛依据） */
  capabilities: Capabilities | null;

  /** 登录 */
  login: (username: string, password: string) => Promise<void>;
  /** 注册 */
  register: (username: string, email: string, password: string) => Promise<void>;
  /** 登出 */
  logout: () => void;
  /** 刷新令牌 */
  refresh: () => Promise<void>;
  /** 检查登录状态 */
  checkAuth: () => boolean;
  /** 切换数据上下文 */
  setDataContext: (ctx: DataContext) => void;
  /** 刷新当前账号能力 */
  loadCapabilities: () => Promise<void>;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      token: null,
      refreshToken: null,
      user: null,
      isAuthenticated: false,
      isLoading: false,
      dataContext: { type: 'personal' },
      capabilities: null,

      setDataContext: (ctx) => set({ dataContext: ctx }),

      loadCapabilities: async () => {
        try {
          const res = await api.getCapabilities();
          set({ capabilities: res.data });
          // 当前团队上下文已不存在（被移出团队/团队解散）时回退到个人空间
          const ctx = get().dataContext;
          if (ctx.type === 'team' && ctx.teamId
              && !res.data.teams.some((t) => t.id === ctx.teamId)) {
            set({ dataContext: { type: 'personal' } });
          }
        } catch {
          set({ capabilities: null });
        }
      },

      login: async (username, password) => {
        set({ isLoading: true });
        try {
          const res = await api.login(username, password);
          const { accessToken, refreshToken, user } = res.data;
          set({
            token: accessToken,
            refreshToken,
            user,
            isAuthenticated: true,
            isLoading: false
          });
        } catch (error) {
          set({ isLoading: false });
          throw error;
        }
      },

      register: async (username, email, password) => {
        set({ isLoading: true });
        try {
          const res = await api.register(username, email, password);
          const { accessToken, refreshToken, user } = res.data;
          set({
            token: accessToken,
            refreshToken,
            user,
            isAuthenticated: true,
            isLoading: false
          });
        } catch (error) {
          set({ isLoading: false });
          throw error;
        }
      },

      logout: () => {
        set({
          token: null,
          refreshToken: null,
          user: null,
          isAuthenticated: false,
          capabilities: null,
          dataContext: { type: 'personal' }
        });
      },

      refresh: async () => {
        const currentRefreshToken = get().refreshToken;
        if (!currentRefreshToken) {
          get().logout();
          return;
        }
        try {
          const res = await api.refreshToken(currentRefreshToken);
          const { accessToken, refreshToken, user } = res.data;
          set({ token: accessToken, refreshToken, user });
        } catch {
          get().logout();
        }
      },

      checkAuth: () => {
        return !!get().token;
      }
    }),
    {
      name: 'auth-storage',
      // 只持久化这些字段
      partialize: (state) => ({
        token: state.token,
        refreshToken: state.refreshToken,
        user: state.user,
        isAuthenticated: state.isAuthenticated,
        dataContext: state.dataContext,
        capabilities: state.capabilities
      })
    }
  )
);
