import type { ApiResponse, AuthResponse, Conversation, Message, PageResponse, Skill, SkillConfig } from '../types';
import type { Team, TeamMember, LlmProvider, AgentItem, AgentSkillItem,
  DatasourceItem, DatasourceRequest, ApiEndpointItem, ApiEndpointRequest, ExtensionItem,
  RendererCatalogItem, ConversationRecordScopes, ConversationRecordItem,
  ConversationRecordDetail, Capabilities } from '../types/admin';

const BASE_URL = '/api';

function getToken(): string | null {
  const stored = localStorage.getItem('auth-storage');
  if (stored) {
    try { const parsed = JSON.parse(stored); return parsed?.state?.token || null; } catch { return null; }
  }
  return null;
}

function getDataContextHeader(): string {
  const stored = localStorage.getItem('auth-storage');
  if (stored) {
    try { const parsed = JSON.parse(stored); const ctx = parsed?.state?.dataContext; if (ctx?.type === 'team' && ctx?.teamId) return `team:${ctx.teamId}`; } catch { /* */ }
  }
  return 'personal';
}

async function request<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const token = getToken();
  const headers: Record<string, string> = { 'Content-Type': 'application/json', 'X-Data-Context': getDataContextHeader(), ...(options.headers as Record<string, string> || {}) };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  const response = await fetch(`${BASE_URL}${endpoint}`, { ...options, headers });
  if (!response.ok) {
    if (response.status === 403) {
      localStorage.removeItem('auth-storage');
      window.location.href = '/login';
      throw new Error('无权限访问');
    }
    const errorBody = await response.json().catch(() => ({ message: '请求失败' })); throw new Error(errorBody.message || `HTTP ${response.status}`);
  }
  return response.json();
}

async function publicRequest<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json', ...(options.headers as Record<string, string> || {}) };
  const response = await fetch(`${BASE_URL}${endpoint}`, { ...options, headers });
  if (!response.ok) { const errorBody = await response.json().catch(() => ({ message: '请求失败' })); throw new Error(errorBody.message || `HTTP ${response.status}`); }
  return response.json();
}

// ========== 认证 ==========
export async function register(username: string, email: string, password: string): Promise<ApiResponse<AuthResponse>> { return request('/auth/register', { method: 'POST', body: JSON.stringify({ username, email, password }) }); }
export async function login(username: string, password: string): Promise<ApiResponse<AuthResponse>> { return request('/auth/login', { method: 'POST', body: JSON.stringify({ username, password }) }); }
export async function refreshToken(refreshToken: string): Promise<ApiResponse<AuthResponse>> { return request('/auth/refresh', { method: 'POST', body: JSON.stringify({ refreshToken }) }); }

// ========== 对话 ==========
export async function getConversations(page = 0, size = 20): Promise<ApiResponse<PageResponse<Conversation>>> { return request(`/conversations?page=${page}&size=${size}`); }
export async function getConversation(id: number): Promise<ApiResponse<Conversation>> { return request(`/conversations/${id}`); }
export async function createConversation(title?: string, model?: string, teamId?: number, agentId?: number): Promise<ApiResponse<Conversation>> { return request('/conversations', { method: 'POST', body: JSON.stringify({ title, model, teamId, agentId }) }); }
export async function updateConversation(id: number, data: { title?: string; model?: string; systemPrompt?: string }): Promise<ApiResponse<Conversation>> { return request(`/conversations/${id}`, { method: 'PUT', body: JSON.stringify(data) }); }
export async function deleteConversation(id: number): Promise<ApiResponse<null>> { return request(`/conversations/${id}`, { method: 'DELETE' }); }

// ========== 消息 ==========
export async function getMessages(conversationId: number, page = 0, size = 50): Promise<ApiResponse<PageResponse<Message>>> { return request(`/conversations/${conversationId}/messages?page=${page}&size=${size}`); }

// ========== 账号能力 ==========
export async function getCapabilities(): Promise<ApiResponse<Capabilities>> {
  return request('/capabilities');
}

// ========== 会话记录（管理视角） ==========
export async function getConversationRecordScopes(): Promise<ApiResponse<ConversationRecordScopes>> {
  return request('/conversation-records/scopes');
}

export async function listConversationRecords(params: {
  scope?: string; teamId?: number; keyword?: string; agentId?: number; userId?: number;
  status?: string; from?: string; to?: string; includeDeleted?: boolean; page?: number; size?: number;
}): Promise<ApiResponse<PageResponse<ConversationRecordItem>>> {
  const q = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') q.set(key, String(value));
  });
  return request(`/conversation-records?${q.toString()}`);
}

export async function getConversationRecord(id: number): Promise<ApiResponse<ConversationRecordDetail>> {
  return request(`/conversation-records/${id}`);
}

export async function deleteConversationRecord(id: number): Promise<ApiResponse<null>> {
  return request(`/conversation-records/${id}`, { method: 'DELETE' });
}

// ========== 技能 ==========
export async function getSkills(conversationId?: number, publishStatus?: string): Promise<ApiResponse<Skill[]>> {
  const params = new URLSearchParams();
  if (conversationId) params.set('conversationId', String(conversationId));
  if (publishStatus) params.set('publishStatus', publishStatus);
  const qs = params.toString();
  return request(`/skills${qs ? `?${qs}` : ''}`);
}

// ========== 上架审核（技能包） ==========
export async function publishSkill(id: number): Promise<ApiResponse<Skill>> {
  return request(`/skills/${id}/publish`, { method: 'POST' });
}
export async function withdrawSkill(id: number): Promise<ApiResponse<Skill>> {
  return request(`/skills/${id}/publish/withdraw`, { method: 'POST' });
}
export async function reviewSkill(id: number, approve: boolean, reason?: string): Promise<ApiResponse<Skill>> {
  return request(`/skills/${id}/review`, { method: 'POST', body: JSON.stringify({ approve, reason }) });
}
export async function getSkill(id: number, conversationId?: number): Promise<ApiResponse<Skill>> { return request(`/skills/${id}${conversationId ? `?conversationId=${conversationId}` : ''}`); }
export async function configureSkill(id: number, config: Record<string, unknown>): Promise<ApiResponse<SkillConfig>> { return request(`/skills/${id}/config`, { method: 'POST', body: JSON.stringify({ config }) }); }
export async function activateSkill(skillId: number, conversationId: number, configOverrides?: Record<string, unknown>): Promise<ApiResponse<Skill>> { return request(`/skills/${skillId}/activate?conversationId=${conversationId}`, { method: 'POST', body: JSON.stringify({ configOverrides }) }); }
export async function deactivateSkill(skillId: number, conversationId: number): Promise<ApiResponse<null>> { return request(`/skills/${skillId}/deactivate?conversationId=${conversationId}`, { method: 'POST' }); }
export async function createSkill(data: Record<string, unknown>): Promise<ApiResponse<Skill>> { return request('/skills', { method: 'POST', body: JSON.stringify(data) }); }
export async function updateSkill(id: number, data: Record<string, unknown>): Promise<ApiResponse<Skill>> { return request(`/skills/${id}`, { method: 'PUT', body: JSON.stringify(data) }); }
export async function deleteSkill(id: number): Promise<ApiResponse<null>> { return request(`/skills/${id}`, { method: 'DELETE' }); }
export async function updateToolBindings(skillId: number, requiredTools: string[]): Promise<ApiResponse<Skill>> { return request(`/skills/${skillId}/tool-bindings`, { method: 'PUT', body: JSON.stringify({ requiredTools }) }); }

// ========== 文件下载 ==========
export async function downloadFile(downloadUrl: string, filename: string): Promise<void> {
  const token = getToken();
  const response = await fetch(downloadUrl, { headers: token ? { 'Authorization': `Bearer ${token}` } : {} });
  if (!response.ok) throw new Error(`下载失败: HTTP ${response.status}`);
  const blob = await response.blob(); const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = filename; document.body.appendChild(a); a.click(); document.body.removeChild(a); URL.revokeObjectURL(url);
}
export function getDownloadUrl(path: string): string { return path; }

// ========== 团队管理 ==========
export async function getTeams(): Promise<ApiResponse<Team[]>> { return request('/teams'); }
export async function getTeam(id: number): Promise<ApiResponse<Team>> { return request(`/teams/${id}`); }
export async function createTeam(name: string, description?: string): Promise<ApiResponse<Team>> { return request('/teams', { method: 'POST', body: JSON.stringify({ name, description }) }); }
export async function updateTeam(id: number, data: { name?: string; description?: string }): Promise<ApiResponse<Team>> { return request(`/teams/${id}`, { method: 'PUT', body: JSON.stringify(data) }); }
export async function deleteTeam(id: number): Promise<ApiResponse<null>> { return request(`/teams/${id}`, { method: 'DELETE' }); }
export async function getTeamMembers(teamId: number): Promise<ApiResponse<TeamMember[]>> { return request(`/teams/${teamId}/members`); }
export async function addTeamMember(teamId: number, userId: number, role: string): Promise<ApiResponse<TeamMember>> { return request(`/teams/${teamId}/members`, { method: 'POST', body: JSON.stringify({ userId, role }) }); }
export async function removeTeamMember(teamId: number, userId: number): Promise<ApiResponse<null>> { return request(`/teams/${teamId}/members/${userId}`, { method: 'DELETE' }); }

// ========== LLM 供应商 ==========
export async function getProviders(): Promise<ApiResponse<LlmProvider[]>> { return request('/providers'); }
export async function createProvider(data: Record<string, unknown>): Promise<ApiResponse<LlmProvider>> { return request('/providers', { method: 'POST', body: JSON.stringify(data) }); }
export async function updateProvider(id: number, data: Record<string, unknown>): Promise<ApiResponse<LlmProvider>> { return request(`/providers/${id}`, { method: 'PUT', body: JSON.stringify(data) }); }
export async function deleteProvider(id: number): Promise<ApiResponse<null>> { return request(`/providers/${id}`, { method: 'DELETE' }); }
export async function setDefaultProvider(id: number): Promise<ApiResponse<LlmProvider>> { return request(`/providers/${id}/set-default`, { method: 'POST' }); }

// ========== 智能体 ==========
export async function getAgents(teamId?: number): Promise<ApiResponse<AgentItem[]>> { return request(`/agents${teamId ? `?teamId=${teamId}` : ''}`); }
export async function getAgent(id: number): Promise<ApiResponse<AgentItem>> { return request(`/agents/${id}`); }
export async function createAgent(data: Record<string, unknown>): Promise<ApiResponse<AgentItem>> { return request('/agents', { method: 'POST', body: JSON.stringify(data) }); }
export async function updateAgent(id: number, data: Record<string, unknown>): Promise<ApiResponse<AgentItem>> { return request(`/agents/${id}`, { method: 'PUT', body: JSON.stringify(data) }); }
export async function deleteAgent(id: number): Promise<ApiResponse<null>> { return request(`/agents/${id}`, { method: 'DELETE' }); }
export async function getAgentSkills(agentId: number): Promise<ApiResponse<AgentSkillItem[]>> { return request(`/agents/${agentId}/skills`); }
export async function bindAgentSkill(agentId: number, skillId: number): Promise<ApiResponse<AgentSkillItem>> { return request(`/agents/${agentId}/skills`, { method: 'POST', body: JSON.stringify({ skillId }) }); }
export async function unbindAgentSkill(agentId: number, skillId: number): Promise<ApiResponse<null>> { return request(`/agents/${agentId}/skills/${skillId}`, { method: 'DELETE' }); }
export async function regenerateShareToken(agentId: number): Promise<ApiResponse<{ shareToken: string }>> { return request(`/agents/${agentId}/regenerate-share-token`, { method: 'POST' }); }
export async function generateAesKey(agentId: number): Promise<ApiResponse<{ aesKey: string }>> { return request(`/agents/${agentId}/generate-aes-key`, { method: 'POST' }); }

// ========== 智能体公开访问 ==========
export interface AgentPublicInfoFull { name: string; logo: string; description: string; decryptedUsername: string | null; }
export async function getAgentPublicInfo(shareToken: string, encryptedUser?: string): Promise<ApiResponse<AgentPublicInfoFull>> { return publicRequest(`/agent/public/${shareToken}${encryptedUser ? `?user=${encodeURIComponent(encryptedUser)}` : ''}`); }
export async function listPublicConversations(shareToken: string, sessionId: string, encryptedUser?: string): Promise<ApiResponse<{ conversationId: number; title: string; messageCount: number; createdAt: string }[]>> {
  const params = new URLSearchParams({ sessionId }); if (encryptedUser) params.set('user', encryptedUser);
  return publicRequest(`/agent/public/${shareToken}/conversations?${params}`);
}
export async function getPublicMessages(shareToken: string, conversationId: number): Promise<ApiResponse<Message[]>> { return publicRequest(`/agent/public/${shareToken}/conversations/${conversationId}/messages`); }
export async function createPublicConversation(shareToken: string, sessionId: string, encryptedUser?: string): Promise<ApiResponse<{ conversationId: number; title: string }>> {
  const params = new URLSearchParams({ sessionId }); if (encryptedUser) params.set('user', encryptedUser);
  return publicRequest(`/agent/public/${shareToken}/conversations?${params}`, { method: 'POST' });
}
export async function sendPublicMessage(shareToken: string, conversationId: number, content: string, encryptedUser?: string): Promise<Response> {
  const params = new URLSearchParams({ conversationId: String(conversationId) }); if (encryptedUser) params.set('user', encryptedUser);
  return fetch(`${BASE_URL}/agent/public/${shareToken}/messages?${params}`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ content }) });
}
export async function checkPublicLimit(shareToken: string, conversationId: number, encryptedUser?: string): Promise<ApiResponse<{ limited: boolean }>> { return publicRequest(`/agent/public/${shareToken}/check-limit?conversationId=${conversationId}${encryptedUser ? `&user=${encodeURIComponent(encryptedUser)}` : ''}`); }

// ========== 业务系统数据库（数据源） ==========
export async function getDatasources(): Promise<ApiResponse<DatasourceItem[]>> { return request('/datasources'); }
export async function getDatasource(id: number): Promise<ApiResponse<DatasourceItem>> { return request(`/datasources/${id}`); }
export async function createDatasource(data: DatasourceRequest): Promise<ApiResponse<DatasourceItem>> { return request('/datasources', { method: 'POST', body: JSON.stringify(data) }); }
export async function updateDatasource(id: number, data: DatasourceRequest): Promise<ApiResponse<DatasourceItem>> { return request(`/datasources/${id}`, { method: 'PUT', body: JSON.stringify(data) }); }
export async function deleteDatasource(id: number): Promise<ApiResponse<null>> { return request(`/datasources/${id}`, { method: 'DELETE' }); }
export async function testDatasource(id: number): Promise<ApiResponse<DatasourceItem>> { return request(`/datasources/${id}/test`, { method: 'POST' }); }
export async function introspectDatasource(id: number): Promise<ApiResponse<{ tables: number }>> { return request(`/datasources/${id}/introspect`, { method: 'POST' }); }
export async function getDatasourceTables(id: number): Promise<ApiResponse<{ tableName: string; comment: string; syncedAt: string }[]>> { return request(`/datasources/${id}/tables`); }
export async function getDatasourceColumns(id: number, tableName: string): Promise<ApiResponse<{ column: string; type: string; nullable: boolean; comment: string }[]>> { return request(`/datasources/${id}/tables/${encodeURIComponent(tableName)}/columns`); }
export async function previewDatasourceQuery(id: number, sql: string, params?: Record<string, unknown>): Promise<ApiResponse<Record<string, unknown>[]>> { return request(`/datasources/${id}/preview-query`, { method: 'POST', body: JSON.stringify({ sql, params }) }); }

// ========== 业务系统接口 ==========
export async function getInterfaces(): Promise<ApiResponse<ApiEndpointItem[]>> { return request('/interfaces'); }
export async function getInterface(id: number): Promise<ApiResponse<ApiEndpointItem>> { return request(`/interfaces/${id}`); }
export async function createInterface(data: ApiEndpointRequest): Promise<ApiResponse<ApiEndpointItem>> { return request('/interfaces', { method: 'POST', body: JSON.stringify(data) }); }
export async function updateInterface(id: number, data: ApiEndpointRequest): Promise<ApiResponse<ApiEndpointItem>> { return request(`/interfaces/${id}`, { method: 'PUT', body: JSON.stringify(data) }); }
export async function deleteInterface(id: number): Promise<ApiResponse<null>> { return request(`/interfaces/${id}`, { method: 'DELETE' }); }
export async function testInterface(id: number, params?: Record<string, unknown>): Promise<ApiResponse<{ result: string }>> { return request(`/interfaces/${id}/test`, { method: 'POST', body: JSON.stringify(params || {}) }); }

// ========== 扩展包 ==========
export async function getExtensions(publishStatus?: string): Promise<ApiResponse<ExtensionItem[]>> {
  return request(`/extensions${publishStatus ? `?publishStatus=${publishStatus}` : ''}`);
}

// ========== 上架审核（扩展包） ==========
export async function publishExtension(extCode: string): Promise<ApiResponse<ExtensionItem>> {
  return request(`/extensions/${extCode}/publish`, { method: 'POST' });
}
export async function withdrawExtension(extCode: string): Promise<ApiResponse<ExtensionItem>> {
  return request(`/extensions/${extCode}/publish/withdraw`, { method: 'POST' });
}
export async function reviewExtension(extCode: string, approve: boolean, reason?: string): Promise<ApiResponse<ExtensionItem>> {
  return request(`/extensions/${extCode}/review`, { method: 'POST', body: JSON.stringify({ approve, reason }) });
}
export async function getExtension(extCode: string): Promise<ApiResponse<ExtensionItem>> { return request(`/extensions/${extCode}`); }
export async function importExtension(file: File): Promise<ApiResponse<{ extCode: string; version: string; status: string }>> {
  const token = getToken();
  const form = new FormData();
  form.append('file', file);
  const response = await fetch(`${BASE_URL}/extensions/import`, {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: form,
  });
  if (!response.ok) {
    const body = await response.json().catch(() => ({ message: '导入失败' }));
    throw new Error(body.message || `HTTP ${response.status}`);
  }
  return response.json();
}
export async function enableExtension(extCode: string): Promise<ApiResponse<ExtensionItem>> { return request(`/extensions/${extCode}/enable`, { method: 'POST' }); }
export async function disableExtension(extCode: string): Promise<ApiResponse<ExtensionItem>> { return request(`/extensions/${extCode}/disable`, { method: 'POST' }); }
export async function reloadExtension(extCode: string): Promise<ApiResponse<ExtensionItem>> { return request(`/extensions/${extCode}/reload`, { method: 'POST' }); }
export async function uninstallExtension(extCode: string): Promise<ApiResponse<null>> { return request(`/extensions/${extCode}`, { method: 'DELETE' }); }
export async function getExtensionTools(): Promise<ApiResponse<{ toolKey: string; toolCode: string; extensionId: number; displayName: string; description: string; toolKind: string; riskLevel: string; anonymousAllowed: boolean; renderType: string }[]>> { return request('/extensions/tools'); }
export async function getExtensionRenderers(): Promise<ApiResponse<RendererCatalogItem[]>> { return request('/extensions/renderers'); }
export async function getAgentExtensions(agentId: number): Promise<ApiResponse<{ extensionId: number; extCode: string; extName: string; version: string; status: string; enabled: boolean }[]>> { return request(`/agents/${agentId}/extensions`); }
export async function bindAgentExtension(agentId: number, extCode: string): Promise<ApiResponse<unknown>> { return request(`/agents/${agentId}/extensions`, { method: 'POST', body: JSON.stringify({ extCode }) }); }
export async function unbindAgentExtension(agentId: number, extCode: string): Promise<ApiResponse<null>> { return request(`/agents/${agentId}/extensions/${extCode}`, { method: 'DELETE' }); }

// ========== 技能包导入导出与预览 ==========
export async function previewSkill(id: number): Promise<ApiResponse<{ prompt: string; toolKeys: string[]; datasourceCodes: string[]; apiCodes: string[]; warnings: string[] }>> { return request(`/skills/${id}/preview`); }
export async function previewSkillDraft(data: Record<string, unknown>): Promise<ApiResponse<{ prompt: string; toolKeys: string[]; datasourceCodes: string[]; apiCodes: string[]; warnings: string[] }>> { return request('/skills/preview', { method: 'POST', body: JSON.stringify(data) }); }
export async function importSkill(file: File): Promise<ApiResponse<Skill>> {
  const token = getToken();
  const form = new FormData();
  form.append('file', file);
  const response = await fetch(`${BASE_URL}/skills/import`, {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: form,
  });
  if (!response.ok) {
    const body = await response.json().catch(() => ({ message: '导入失败' }));
    throw new Error(body.message || `HTTP ${response.status}`);
  }
  return response.json();
}
export function getSkillExportUrl(id: number): string { return `${BASE_URL}/skills/${id}/export`; }
