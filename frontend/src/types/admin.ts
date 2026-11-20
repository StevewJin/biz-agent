import type { Message } from './index';

export interface Team {
  id: number;
  name: string;
  description: string | null;
  owner: { id: number };
  createdAt: string;
  updatedAt: string;
}

export interface TeamMember {
  id: number;
  team: { id: number };
  user: { id: number; username?: string };
  role: string;
}

export interface LlmProvider {
  id: number;
  name: string;
  providerType: string;
  baseUrl: string;
  models: string[];
  isDefault: boolean;
  enabled: boolean;
  createdAt: string;
}

export interface AgentItem {
  id: number;
  name: string;
  logo: string | null;
  description: string | null;
  systemPrompt: string | null;
  scope: string;
  provider: { id: number; name?: string } | null;
  shareToken: string | null;
  aesKey: string | null;
  enabled: boolean;
  owner: { id: number; username?: string } | null;
  team: { id: number } | null;
  createdAt: string;
}

export interface AgentSkillItem {
  id: number;
  agent: { id: number };
  skill: { id: number; name: string; displayName: string; description: string; icon: string };
  configOverrides: Record<string, unknown> | null;
}

export interface AgentPublicInfo {
  name: string;
  logo: string;
  description: string;
}

// ========== 业务系统数据库（数据源） ==========
export interface DatasourceItem {
  id: number;
  dsCode: string;
  dsName: string;
  dbType: string;
  jdbcUrl: string;
  driverClass: string | null;
  driverJarPath: string | null;
  username: string | null;
  passwordMasked: string | null;
  schemaName: string | null;
  readOnly: boolean;
  queryMaxRows: number;
  queryTimeoutSec: number;
  ownerId: number;
  teamId: number | null;
  visibility: string;
  description: string | null;
  lastTestAt: string | null;
  lastTestOk: boolean | null;
  enabled: boolean;
  tableCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface DatasourceRequest {
  dsCode?: string;
  dsName?: string;
  dbType?: string;
  jdbcUrl?: string;
  driverClass?: string;
  driverJarPath?: string;
  username?: string;
  password?: string;
  schemaName?: string;
  readOnly?: boolean;
  queryMaxRows?: number;
  queryTimeoutSec?: number;
  visibility?: string;
  description?: string;
  enabled?: boolean;
}

// ========== 业务系统接口 ==========
export interface ApiEndpointItem {
  id: number;
  apiCode: string;
  apiName: string;
  baseUrl: string;
  path: string;
  httpMethod: string;
  authType: string;
  authMasked: string | null;
  defaultHeaders: Record<string, string> | null;
  paramSchema: string | null;
  responseSchema: string | null;
  requestExample: string | null;
  timeoutMs: number;
  ownerId: number;
  teamId: number | null;
  visibility: string;
  description: string | null;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ApiEndpointRequest {
  apiCode?: string;
  apiName?: string;
  baseUrl?: string;
  path?: string;
  httpMethod?: string;
  authType?: string;
  authConfig?: Record<string, unknown>;
  defaultHeaders?: Record<string, string>;
  paramSchema?: string;
  responseSchema?: string;
  requestExample?: string;
  timeoutMs?: number;
  visibility?: string;
  description?: string;
  enabled?: boolean;
}

// ========== 扩展包 ==========
export interface ExtensionToolItem {
  toolKey: string;
  toolCode: string;
  displayName: string | null;
  description: string | null;
  toolKind: string;
  riskLevel: string;
  anonymousAllowed: boolean;
  requiresConfirm: boolean;
  renderType: string | null;
  parameterSchema: string | null;
  bindingJson: string | null;
}

export interface ExtensionRendererItem {
  renderType: string;
  entry: string | null;
  supportedTools: string | null;
}

export interface ExtensionItem {
  id: number;
  extCode: string;
  extName: string;
  version: string;
  packType: string;
  status: string;
  systemPack: boolean;
  ownerId: number | null;
  teamId: number | null;
  /** personal / team / public */
  visibility: string;
  /** draft / pending / approved / rejected */
  publishStatus: string;
  rejectReason: string | null;
  publishedAt: string | null;
  entryFile: string | null;
  sha256: string | null;
  errorMsg: string | null;
  installedAt: string;
  updatedAt: string;
  loaded: boolean;
  vendor: string | null;
  description: string | null;
  icon: string | null;
  tools: ExtensionToolItem[];
  renderers: ExtensionRendererItem[];
}

export interface RendererCatalogItem {
  renderType: string;
  extCode: string;
  version: string;
  entry: string;
  enabled: boolean;
}

// ========== 会话记录 ==========

/** 当前账号可用的会话记录数据范围 */
export interface ConversationRecordScopes {
  /** 可查看全平台会话（仅系统管理员） */
  all: boolean;
  /** 可查看团队会话的团队 ID 列表 */
  managedTeamIds: number[];
}

export interface ConversationRecordItem {
  id: number;
  title: string;
  status: string;
  model: string;
  messageCount: number;
  userId: number | null;
  userName: string | null;
  teamId: number | null;
  teamName: string | null;
  agentId: number | null;
  agentName: string | null;
  manageable: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ConversationRecordDetail {
  conversation: ConversationRecordItem;
  messages: Message[];
}

// ========== 账号能力（前端菜单/按钮收敛） ==========

export interface TeamCapability {
  id: number;
  name: string;
  /** owner / admin / member */
  role: string;
  manageable: boolean;
}

export interface Capabilities {
  userId: number | null;
  username: string | null;
  admin: boolean;
  managedTeamIds: number[];
  teams: TeamCapability[];
  permissions: {
    manageProviders: boolean;
    manageExtensions: boolean;
    manageBuiltinSkills: boolean;
    viewAllConversations: boolean;
    manageTeams: boolean;
  };
}
