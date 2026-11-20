import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '../../stores/authStore';
import { LogOut, User, Settings } from 'lucide-react';

interface UserMenuProps {
  userName: string;
}

export default function UserMenu({ userName }: UserMenuProps) {
  const logout = useAuthStore((s) => s.logout);
  const navigate = useNavigate();

  return (
    <div className="p-4 border-t border-gray-100">
      <div className="flex items-center gap-3">
        <div className="w-8 h-8 bg-purple-100 rounded-full flex items-center justify-center">
          <User size={16} className="text-purple-600" />
        </div>
        <div className="flex-1 min-w-0">
          <div className="text-sm font-medium text-gray-700 truncate">{userName}</div>
        </div>
        <button onClick={() => navigate('/admin')} className="p-1.5 text-gray-400 hover:text-purple-500 rounded-lg hover:bg-purple-50 transition cursor-pointer" title="管理控制台">
          <Settings size={16} />
        </button>
        <button onClick={logout} className="p-1.5 text-gray-400 hover:text-red-500 rounded-lg hover:bg-red-50 transition cursor-pointer" title="退出登录">
          <LogOut size={16} />
        </button>
      </div>
    </div>
  );
}
