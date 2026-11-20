import { lazy, Suspense } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useAuthStore } from './stores/authStore';
import ProtectedRoute from './components/common/ProtectedRoute';
import RequireAdmin from './components/common/RequireAdmin';
import { ConfirmHost, Toaster } from './components/common/feedback';

// 路由级代码分割：管理后台与聊天页（含图表库）按需加载
const LoginPage = lazy(() => import('./pages/LoginPage'));
const RegisterPage = lazy(() => import('./pages/RegisterPage'));
const ChatPage = lazy(() => import('./pages/ChatPage'));
const SkillMarketplacePage = lazy(() => import('./pages/SkillMarketplacePage'));
const AdminLayout = lazy(() => import('./components/admin/AdminLayout'));
const TeamManagementPage = lazy(() => import('./pages/admin/TeamManagementPage'));
const LlmProviderManagementPage = lazy(() => import('./pages/admin/LlmProviderManagementPage'));
const DatasourceManagementPage = lazy(() => import('./pages/admin/DatasourceManagementPage'));
const ApiEndpointManagementPage = lazy(() => import('./pages/admin/ApiEndpointManagementPage'));
const ExtensionManagementPage = lazy(() => import('./pages/admin/ExtensionManagementPage'));
const ExtensionDetailPage = lazy(() => import('./pages/admin/ExtensionDetailPage'));
const AdminSkillManagementPage = lazy(() => import('./pages/admin/AdminSkillManagementPage'));
const SkillEditPage = lazy(() => import('./pages/admin/SkillEditPage'));
const AgentManagementPage = lazy(() => import('./pages/admin/AgentManagementPage'));
const AgentDetailPage = lazy(() => import('./pages/admin/AgentDetailPage'));
const ConversationRecordsPage = lazy(() => import('./pages/admin/ConversationRecordsPage'));
const ReviewCenterPage = lazy(() => import('./pages/admin/ReviewCenterPage'));
const ProfilePage = lazy(() => import('./pages/admin/ProfilePage'));
const AgentPublicPage = lazy(() => import('./pages/AgentPublicPage'));

function RouteFallback() {
  return (
    <div className="flex h-screen items-center justify-center bg-white text-sm text-gray-400">
      加载中…
    </div>
  );
}

function App() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  return (
    <BrowserRouter>
      <Toaster />
      <ConfirmHost />
      <Suspense fallback={<RouteFallback />}>
        <Routes>
        {/* 认证页面：登录后直接进入管理后台 */}
        <Route path="/login" element={isAuthenticated ? <Navigate to="/admin" /> : <LoginPage />} />
        <Route path="/register" element={isAuthenticated ? <Navigate to="/admin" /> : <RegisterPage />} />

        {/* 测试对话（独立全屏布局，从后台菜单进入） */}
        <Route path="/chat" element={<ProtectedRoute><ChatPage /></ProtectedRoute>} />
        <Route path="/chat/:conversationId" element={<ProtectedRoute><ChatPage /></ProtectedRoute>} />

        {/* 技能市场 */}
        <Route path="/skills" element={<ProtectedRoute><SkillMarketplacePage /></ProtectedRoute>} />

        {/* 管理后台 */}
        <Route path="/admin" element={<ProtectedRoute><AdminLayout /></ProtectedRoute>}>
          <Route index element={<Navigate to="agents" />} />
          <Route path="teams" element={<TeamManagementPage />} />
          <Route path="providers" element={<RequireAdmin><LlmProviderManagementPage /></RequireAdmin>} />
          <Route path="datasources" element={<DatasourceManagementPage />} />
          <Route path="interfaces" element={<ApiEndpointManagementPage />} />
          <Route path="extensions" element={<ExtensionManagementPage />} />
          <Route path="extensions/:code" element={<ExtensionDetailPage />} />
          <Route path="skills" element={<AdminSkillManagementPage />} />
          <Route path="skills/new" element={<SkillEditPage />} />
          <Route path="skills/:id/edit" element={<SkillEditPage />} />
          <Route path="agents" element={<AgentManagementPage />} />
          <Route path="agents/:id" element={<AgentDetailPage />} />
          <Route path="conversations" element={<ConversationRecordsPage />} />
          <Route path="reviews" element={<RequireAdmin><ReviewCenterPage /></RequireAdmin>} />
          <Route path="profile" element={<ProfilePage />} />
        </Route>

        {/* 智能体公开访问 (无需登录) */}
        <Route path="/agent/:shareToken" element={<AgentPublicPage />} />

        {/* 默认重定向 */}
        <Route path="*" element={<Navigate to="/admin" />} />
        </Routes>
      </Suspense>
    </BrowserRouter>
  );
}

export default App;
