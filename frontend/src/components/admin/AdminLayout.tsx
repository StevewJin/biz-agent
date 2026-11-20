import { useEffect } from 'react';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import ContextSwitcher from './ContextSwitcher';
import { useAuthStore } from '../../stores/authStore';
import {
  Users, Cpu, Wrench, Puzzle, Bot, User, Database, Cloud, Boxes,
  MessageSquare, History, LogOut, ShieldCheck,
} from 'lucide-react';

interface MenuItem {
  path: string;
  label: string;
  icon: typeof Users;
  /** 仅系统管理员可见 */
  adminOnly?: boolean;
}

interface MenuGroup {
  title: string;
  items: MenuItem[];
}

const MENU_GROUPS: MenuGroup[] = [
  {
    title: '工作台',
    items: [
      { path: '/chat', label: '测试对话', icon: MessageSquare },
      { path: '/admin/conversations', label: '会话记录', icon: History },
    ],
  },
  {
    title: '业务配置',
    items: [
      { path: '/admin/datasources', label: '业务数据库', icon: Database },
      { path: '/admin/interfaces', label: '业务接口', icon: Cloud },
    ],
  },
  {
    title: '能力中心',
    items: [
      { path: '/admin/extensions', label: '扩展包', icon: Wrench },
      { path: '/admin/skills', label: '技能包', icon: Puzzle },
      { path: '/admin/reviews', label: '上架审核', icon: ShieldCheck, adminOnly: true },
    ],
  },
  {
    title: '工作区',
    items: [
      { path: '/admin/agents', label: '智能体', icon: Bot },
      { path: '/admin/teams', label: '团队', icon: Users },
      { path: '/admin/providers', label: 'LLM 供应商', icon: Cpu, adminOnly: true },
    ],
  },
  {
    title: '账户',
    items: [{ path: '/admin/profile', label: '个人信息', icon: User }],
  },
];

function currentTitle(pathname: string): string {
  const all = MENU_GROUPS.flatMap((g) => g.items);
  const matched = all
    .filter((item) => pathname.startsWith(item.path))
    .sort((a, b) => b.path.length - a.path.length)[0];
  return matched ? matched.label : '管理控制台';
}

export default function AdminLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const logout = useAuthStore((s) => s.logout);
  const loadCapabilities = useAuthStore((s) => s.loadCapabilities);
  const admin = useAuthStore((s) => s.capabilities?.admin ?? false);
  const username = useAuthStore((s) => s.user?.username ?? '');

  useEffect(() => { loadCapabilities(); }, [loadCapabilities]);

  const visibleGroups = MENU_GROUPS
    .map((group) => ({
      ...group,
      items: group.items.filter((item) => !item.adminOnly || admin),
    }))
    .filter((group) => group.items.length > 0);

  return (
    <div className="flex h-screen bg-white text-gray-900">
      {/* 侧边栏 */}
      <aside className="flex w-60 shrink-0 flex-col border-r border-gray-200 bg-white">
        <div className="flex items-center gap-2.5 px-5 py-5">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-gray-900 text-white">
            <Boxes size={17} />
          </div>
          <div className="leading-tight">
            <div className="text-sm font-semibold tracking-tight text-gray-900">BizAgent</div>
            <div className="text-[11px] text-gray-400">
              {admin ? '系统管理员' : '管理控制台'}
              {!admin && username ? ` · ${username}` : ''}
            </div>
          </div>
        </div>

        <nav className="flex-1 overflow-y-auto px-3 pb-4">
          {visibleGroups.map((group) => (
            <div key={group.title} className="mb-5">
              <div className="px-2 pb-1.5 text-[11px] font-medium uppercase tracking-wider text-gray-400">
                {group.title}
              </div>
              <div className="space-y-0.5">
                {group.items.map((item) => {
                  const active = location.pathname.startsWith(item.path);
                  return (
                    <button
                      key={item.path}
                      onClick={() => navigate(item.path)}
                      className={`group flex w-full items-center gap-2.5 rounded-lg px-2.5 py-2 text-[13px] transition-colors cursor-pointer ${
                        active
                          ? 'bg-gray-100 font-medium text-gray-900'
                          : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'
                      }`}
                    >
                      <item.icon
                        size={16}
                        className={active ? 'text-indigo-600' : 'text-gray-400 group-hover:text-gray-500'}
                      />
                      {item.label}
                    </button>
                  );
                })}
              </div>
            </div>
          ))}
        </nav>

        <div className="border-t border-gray-100 p-3">
          <button
            onClick={() => { logout(); navigate('/login'); }}
            className="flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-[13px] text-gray-500 transition-colors hover:bg-gray-50 hover:text-gray-800 cursor-pointer"
          >
            <LogOut size={15} /> 退出登录
          </button>
        </div>
      </aside>

      {/* 右侧内容 */}
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex h-14 shrink-0 items-center justify-between border-b border-gray-200 bg-white px-8">
          <div className="flex items-center gap-2 text-sm">
            <span className="text-gray-400">控制台</span>
            <span className="text-gray-300">/</span>
            <span className="font-medium text-gray-800">{currentTitle(location.pathname)}</span>
          </div>
          <ContextSwitcher />
        </header>

        <main className="flex-1 overflow-y-auto bg-white">
          <div className="mx-auto w-full max-w-6xl px-8 py-7">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
}
