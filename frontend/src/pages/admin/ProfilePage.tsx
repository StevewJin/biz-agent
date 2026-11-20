import { useAuthStore } from '../../stores/authStore';
import { Badge, Card, PageHeader } from '../../components/admin/ui';

export default function ProfilePage() {
  const { user } = useAuthStore();

  const rows = [
    { label: '用户名', value: user?.username || '-' },
    { label: '邮箱', value: user?.email || '-' },
    {
      label: '角色',
      value: <Badge tone={user?.role === 'admin' ? 'indigo' : 'gray'}>{user?.role === 'admin' ? '管理员' : '普通用户'}</Badge>,
    },
  ];

  return (
    <div>
      <PageHeader title="个人信息" description="当前登录账号的基本信息与权限角色。" />
      <Card title="账号" description="如需修改密码或角色，请联系管理员" className="max-w-xl" flush>
        <div className="divide-y divide-gray-100">
          {rows.map((row) => (
            <div key={row.label} className="flex items-center justify-between px-5 py-3.5 text-sm">
              <span className="text-gray-500">{row.label}</span>
              <span className="font-medium text-gray-900">{row.value}</span>
            </div>
          ))}
        </div>
      </Card>
    </div>
  );
}
