import { useState, useEffect } from 'react';
import { useAuthStore } from '../../stores/authStore';
import { useAdminStore } from '../../stores/adminStore';
import { Users, User, ChevronsUpDown, Check } from 'lucide-react';

export default function ContextSwitcher() {
  const { dataContext, setDataContext } = useAuthStore();
  const { teams, loadTeams } = useAdminStore();
  const [open, setOpen] = useState(false);

  useEffect(() => { loadTeams(); }, [loadTeams]);

  const label = dataContext.type === 'team'
    ? `团队 · ${dataContext.teamName || '未知'}`
    : '个人空间';

  return (
    <div className="relative">
      <button
        onClick={() => setOpen(!open)}
        className="inline-flex h-8 items-center gap-2 rounded-lg border border-gray-200 bg-white px-2.5 text-[13px] text-gray-700 transition-colors hover:bg-gray-50 cursor-pointer"
      >
        {dataContext.type === 'team'
          ? <Users size={14} className="text-indigo-600" />
          : <User size={14} className="text-gray-400" />}
        <span className="max-w-[160px] truncate">{label}</span>
        <ChevronsUpDown size={13} className="text-gray-400" />
      </button>

      {open && (
        <>
          <div className="fixed inset-0 z-10" onClick={() => setOpen(false)} />
          <div className="absolute right-0 z-20 mt-1.5 w-56 overflow-hidden rounded-xl border border-gray-200 bg-white py-1 shadow-lg">
            <div className="px-3 pb-1 pt-1.5 text-[11px] font-medium uppercase tracking-wider text-gray-400">
              数据上下文
            </div>
            <button
              className="flex w-full items-center gap-2 px-3 py-2 text-[13px] text-gray-700 hover:bg-gray-50 cursor-pointer"
              onClick={() => { setDataContext({ type: 'personal' }); setOpen(false); }}
            >
              <User size={14} className="text-gray-400" /> 个人空间
              {dataContext.type === 'personal' && <Check size={14} className="ml-auto text-indigo-600" />}
            </button>

            {teams.length > 0 && <div className="my-1 border-t border-gray-100" />}
            {teams.map((t) => (
              <button
                key={t.id}
                className="flex w-full items-center gap-2 px-3 py-2 text-[13px] text-gray-700 hover:bg-gray-50 cursor-pointer"
                onClick={() => { setDataContext({ type: 'team', teamId: t.id, teamName: t.name }); setOpen(false); }}
              >
                <Users size={14} className="text-gray-400" />
                <span className="truncate">{t.name}</span>
                {dataContext.type === 'team' && dataContext.teamId === t.id && (
                  <Check size={14} className="ml-auto text-indigo-600" />
                )}
              </button>
            ))}
          </div>
        </>
      )}
    </div>
  );
}
