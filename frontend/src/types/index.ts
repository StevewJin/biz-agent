// 用户信息
export interface UserInfo {
  id: number;
  username: string;
  email: string;
  role: string;
}

// 认证响应
export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserInfo;
}

// 对话
export interface Conversation {
  id: number;
  title: string;
  status: string;
  model: string;
  systemPrompt: string | null;
  messageCount: number;
  createdAt: string;
  updatedAt: string;
}

// 思维链条目
export interface ThinkingChainEntry {
  id: number;
  sequenceNum: number;
  type: 'text' | 'thinking' | 'tool_call' | 'tool_result';
  content: string | null;
  toolCallId: string | null;
  toolName: string | null;
  toolInput: Record<string, unknown> | null;
  /** 工具展示名（来自扩展包 manifest） */
  toolDisplayName?: string | null;
  /** 所属扩展包 */
  extensionCode?: string | null;
  extensionName?: string | null;
  /** 工具类型：sql / http / render / export / jar */
  toolKind?: string | null;
  /** 触发该工具的技能包 */
  skillName?: string | null;
  durationMs: number | null;
  /** 前端渲染负载（_render 协议 JSON 字符串） */
  renderJson?: string | null;
}

// 消息
export interface Message {
  id: number;
  role: 'user' | 'assistant' | 'tool';
  content: string | null;
  reasoningContent: string | null;
  toolCallId: string | null;
  toolName: string | null;
  toolInput: Record<string, unknown> | null;
  toolResult: string | null;
  sequenceNum: number;
  createdAt: string;
  /** 思维链 (Phase 3: assistant 消息的中间推理步骤) */
  thinkingChain: ThinkingChainEntry[] | null;
}

// API 统一响应
export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

// 分页响应
export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

// 流式消息块
export interface StreamingChunk {
  type: 'text_delta' | 'thinking_delta' | 'tool_use' | 'tool_result' | 'thinking' | 'error' | 'done' | 'message_saved' | 'title_updated';
  content?: string;
  toolName?: string;
  toolInput?: string;
  toolCallId?: string;
  toolResult?: string;
  toolDisplayName?: string;
  extensionCode?: string;
  extensionName?: string;
  toolKind?: string;
  skillName?: string;
  /** 前端渲染负载（_render 协议 JSON 字符串） */
  render?: string;
  durationMs?: number;
  title?: string;
}

// 消息已保存事件
export interface MessageSavedEvent {
  type: string;
  payload: {
    messageId: number;
    sequenceNum: number;
  };
}

// 技能
export interface Skill {
  id: number;
  name: string;
  displayName: string;
  description: string;
  category: string;
  icon: string;
  iconColor?: string | null;
  promptMd?: string | null;
  teamId?: number | null;
  /** personal / team / public */
  visibility?: string | null;
  /** draft / pending / approved / rejected */
  publishStatus?: string | null;
  rejectReason?: string | null;
  publishedAt?: string | null;
  configSchema: Record<string, unknown> | null;
  /** 技能包引用的资源 */
  resources?: {
    datasources?: string[];
    interfaces?: string[];
    extensions?: string[];
    tools?: string[];
  } | null;
  requiredTools: string[] | null;
  keywords?: string | null;
  triggerMode?: string | null;
  schemaMode?: string | null;
  skillVersion?: string | null;
  packSource?: string | null;
  ownerId?: number | null;
  active: boolean;
  configOverrides: Record<string, unknown> | null;
  createdAt: string;
}

// 技能配置
export interface SkillConfig {
  id: number;
  skill: { id: number };
  user: { id: number };
  config: Record<string, unknown>;
}

// 图表数据
export interface ChartDataset {
  label: string;
  data: number[];
}

export interface ChartData {
  chartType: 'bar' | 'line' | 'pie';
  title?: string;
  labels: string[];
  datasets: ChartDataset[];
}
