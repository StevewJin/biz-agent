import { create } from 'zustand';
import * as api from '../services/api';
import type { Message, StreamingChunk } from '../types';

let streamTimeout: ReturnType<typeof setTimeout> | null = null;
const STREAM_TIMEOUT_MS = 600_000; // 10 分钟，匹配后端多轮 LLM 调用

function clearStreamTimeout() {
  if (streamTimeout !== null) {
    clearTimeout(streamTimeout);
    streamTimeout = null;
  }
}

function resetStreamTimeout() {
  clearStreamTimeout();
  streamTimeout = setTimeout(() => {
    usePublicAgentStore.setState({ isStreaming: false });
  }, STREAM_TIMEOUT_MS);
}

interface ToolCallState {
  toolCallId: string;
  toolName: string;
  toolInput: string;
  status: 'executing' | 'done';
  result?: string;
  render?: string;
  durationMs?: number;
  toolDisplayName?: string;
  extensionCode?: string;
  extensionName?: string;
  toolKind?: string;
  skillName?: string;
}

interface ConversationItem {
  id: number;
  title: string;
  messageCount: number;
  createdAt: string;
}

interface PublicAgentState {
  // context
  shareToken: string | null;
  sessionId: string | null;
  encryptedUser: string | undefined;

  conversations: ConversationItem[];
  conversationsLoading: boolean;
  currentConversation: ConversationItem | null;
  messages: Message[];
  messagesLoading: boolean;
  isStreaming: boolean;
  streamingContent: string;
  streamingThinking: string;
  streamingToolCalls: ToolCallState[];
  error: string | null;

  init: (shareToken: string, sessionId: string, encryptedUser?: string) => void;
  loadConversations: () => Promise<void>;
  selectConversation: (id: number) => Promise<void>;
  createConversation: () => Promise<ConversationItem | null>;
  sendMessage: (content: string) => Promise<void>;
  handleStreamChunk: (chunk: StreamingChunk) => void;
  finishStream: () => Promise<void>;
  clearError: () => void;
}

export const usePublicAgentStore = create<PublicAgentState>((set, get) => ({
  shareToken: null,
  sessionId: null,
  encryptedUser: undefined,

  conversations: [],
  conversationsLoading: false,
  currentConversation: null,
  messages: [],
  messagesLoading: false,
  isStreaming: false,
  streamingContent: '',
  streamingThinking: '',
  streamingToolCalls: [],
  error: null,

  init: (shareToken, sessionId, encryptedUser) => {
    set({ shareToken, sessionId, encryptedUser });
  },

  loadConversations: async () => {
    const { shareToken, sessionId, encryptedUser } = get();
    if (!shareToken || !sessionId) return;
    set({ conversationsLoading: true });
    try {
      const r = await api.listPublicConversations(shareToken, sessionId, encryptedUser);
      set({
        conversations: r.data.map((c) => ({
          id: c.conversationId,
          title: c.title,
          messageCount: c.messageCount,
          createdAt: c.createdAt,
        })),
        conversationsLoading: false,
      });
    } catch {
      set({ conversationsLoading: false });
    }
  },

  selectConversation: async (id) => {
    const { shareToken } = get();
    if (!shareToken) return;
    set({ messagesLoading: true, error: null, currentConversation: get().conversations.find((c) => c.id === id) || null });
    try {
      const r = await api.getPublicMessages(shareToken, id);
      set({ messages: r.data, messagesLoading: false });
    } catch {
      set({ messagesLoading: false });
    }
  },

  createConversation: async () => {
    const { shareToken, sessionId, encryptedUser } = get();
    if (!shareToken || !sessionId) return null;
    try {
      const r = await api.createPublicConversation(shareToken, sessionId, encryptedUser);
      const newConv: ConversationItem = {
        id: r.data.conversationId,
        title: r.data.title,
        messageCount: 0,
        createdAt: new Date().toISOString(),
      };
      set((state) => ({
        conversations: [newConv, ...state.conversations],
        currentConversation: newConv,
        messages: [],
        streamingContent: '',
        streamingThinking: '',
        streamingToolCalls: [],
      }));
      return newConv;
    } catch (e: unknown) {
      set({ error: (e as Error).message });
      return null;
    }
  },

  sendMessage: async (content) => {
    const { shareToken, currentConversation, encryptedUser } = get();
    if (!shareToken || !currentConversation) return;

    clearStreamTimeout();
    set({
      isStreaming: true,
      streamingContent: '',
      streamingThinking: '',
      streamingToolCalls: [],
      error: null,
    });
    resetStreamTimeout();

    const userMsg: Message = {
      id: Date.now(),
      role: 'user',
      content,
      reasoningContent: null,
      toolCallId: null,
      toolName: null,
      toolInput: null,
      toolResult: null,
      thinkingChain: null,
      sequenceNum: get().messages.length + 1,
      createdAt: new Date().toISOString(),
    };
    set((state) => ({ messages: [...state.messages, userMsg] }));

    try {
      const response = await api.sendPublicMessage(shareToken, currentConversation.id, content, encryptedUser);
      const reader = response.body?.getReader();
      if (!reader) { set({ isStreaming: false }); return; }
      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';
        for (const line of lines) {
          if (!line.startsWith('data:')) continue;
          try {
            const chunk: StreamingChunk = JSON.parse(line.substring(5).replace(/^ /, ''));
            get().handleStreamChunk(chunk);
          } catch { /* */ }
        }
      }
    } catch (e) {
      set({ isStreaming: false, error: (e as Error).message });
      return;
    }

    await get().finishStream();
  },

  handleStreamChunk: (chunk) => {
    switch (chunk.type) {
      case 'text_delta':
        resetStreamTimeout();
        set((state) => ({
          streamingContent: state.streamingContent + (chunk.content || ''),
        }));
        break;

      case 'thinking_delta':
        resetStreamTimeout();
        set((state) => ({
          streamingThinking: state.streamingThinking + (chunk.content || ''),
        }));
        break;

      case 'tool_use':
        resetStreamTimeout();
        set((state) => ({
          streamingToolCalls: [...state.streamingToolCalls, {
            toolCallId: chunk.toolCallId!,
            toolName: chunk.toolName!,
            toolInput: chunk.toolInput || '{}',
            toolDisplayName: chunk.toolDisplayName,
            extensionCode: chunk.extensionCode,
            extensionName: chunk.extensionName,
            toolKind: chunk.toolKind,
            skillName: chunk.skillName,
            status: 'executing' as const,
          }],
        }));
        break;

      case 'tool_result':
        resetStreamTimeout();
        set((state) => ({
          streamingToolCalls: state.streamingToolCalls.map((tc) =>
            tc.toolCallId === chunk.toolCallId
              ? { ...tc, status: 'done' as const, result: chunk.toolResult, render: chunk.render, durationMs: chunk.durationMs }
              : tc
          ),
        }));
        break;

      case 'done':
        // handled in finishStream
        break;

      case 'error':
        clearStreamTimeout();
        set({ isStreaming: false, error: chunk.content || '未知错误' });
        break;

      case 'title_updated':
        if (chunk.title) {
          set((state) => ({
            currentConversation: state.currentConversation
              ? { ...state.currentConversation, title: chunk.title! }
              : null,
          }));
        }
        break;
    }
  },

  finishStream: async () => {
    const { shareToken, currentConversation } = get();
    clearStreamTimeout();

    // 从后端重新加载消息以获取完整的思维链数据
    if (shareToken && currentConversation) {
      try {
        const r = await api.getPublicMessages(shareToken, currentConversation.id);
        set({ messages: r.data });
      } catch { /* */ }
    }

    // 重新加载对话列表以获取自动生成的标题
    await get().loadConversations();
    const updated = get().conversations.find(c => c.id === currentConversation?.id);

    set({
      isStreaming: false,
      streamingContent: '',
      streamingThinking: '',
      streamingToolCalls: [],
      currentConversation: updated || currentConversation,
    });
  },

  clearError: () => set({ error: null }),
}));
