import { create } from 'zustand';
import * as api from '../services/api';
import { wsManager } from '../services/websocket';
import { useAuthStore } from './authStore';
import type { Conversation, Message, StreamingChunk, ThinkingChainEntry } from '../types';

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
    console.warn('流式响应超时，自动结束');
    // 不清理 streamingContent/streamingThinking，保留已收到的内容
    useChatStore.setState({ isStreaming: false });
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

interface ChatState {
  conversations: Conversation[];
  conversationsLoading: boolean;
  currentConversation: Conversation | null;
  messages: Message[];
  messagesLoading: boolean;
  isStreaming: boolean;
  streamingContent: string;
  streamingThinking: string;
  streamingToolCalls: ToolCallState[];
  error: string | null;

  loadConversations: () => Promise<void>;
  selectConversation: (id: number) => Promise<void>;
  createConversation: (title?: string) => Promise<Conversation | null>;
  deleteConversation: (id: number) => Promise<void>;
  loadMessages: (conversationId: number) => Promise<void>;
  sendMessage: (content: string) => void;
  handleStreamChunk: (chunk: StreamingChunk) => void;
  clearError: () => void;
}

export const useChatStore = create<ChatState>((set, get) => ({
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

  loadConversations: async () => {
    set({ conversationsLoading: true });
    try {
      const res = await api.getConversations();
      set({ conversations: res.data.content, conversationsLoading: false });
    } catch (error: any) {
      set({ error: error.message, conversationsLoading: false });
    }
  },

  selectConversation: async (id) => {
    set({ messagesLoading: true, error: null });
    try {
      const res = await api.getConversation(id);
      set({ currentConversation: res.data });
      const msgRes = await api.getMessages(id);
      set({ messages: msgRes.data.content, messagesLoading: false });
      wsManager.subscribe(id, (chunk) => {
        get().handleStreamChunk(chunk);
      });
    } catch (error: any) {
      set({ error: error.message, messagesLoading: false });
    }
  },

  createConversation: async (title) => {
    try {
      // 团队空间下创建的会话归入团队，便于团队管理员在「会话记录」中审计
      const context = useAuthStore.getState().dataContext;
      const teamId = context.type === 'team' ? context.teamId : undefined;
      const res = await api.createConversation(title || '新对话', undefined, teamId);
      const newConv = res.data;
      set((state) => ({
        conversations: [newConv, ...state.conversations],
        currentConversation: newConv,
        messages: [],
        streamingContent: '',
        streamingThinking: '',
        streamingToolCalls: []
      }));
      wsManager.subscribe(newConv.id, (chunk) => {
        get().handleStreamChunk(chunk);
      });
      return newConv;
    } catch (error: any) {
      set({ error: error.message });
      return null;
    }
  },

  deleteConversation: async (id) => {
    try {
      await api.deleteConversation(id);
      set((state) => ({
        conversations: state.conversations.filter((c) => c.id !== id),
        currentConversation: state.currentConversation?.id === id ? null : state.currentConversation,
        messages: state.currentConversation?.id === id ? [] : state.messages
      }));
    } catch (error: any) {
      set({ error: error.message });
    }
  },

  loadMessages: async (conversationId) => {
    set({ messagesLoading: true });
    try {
      const res = await api.getMessages(conversationId);
      set({ messages: res.data.content, messagesLoading: false });
    } catch (error: any) {
      set({ error: error.message, messagesLoading: false });
    }
  },

  sendMessage: (content) => {
    const { currentConversation } = get();
    if (!currentConversation) return;

    clearStreamTimeout();
    set({
      isStreaming: true,
      streamingContent: '',
      streamingThinking: '',
      streamingToolCalls: [],
      error: null
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
      createdAt: new Date().toISOString()
    };
    set((state) => ({ messages: [...state.messages, userMsg] }));

    wsManager.sendMessage(currentConversation.id, content);
  },

  handleStreamChunk: (chunk) => {
    const { currentConversation } = get();

    switch (chunk.type) {
      case 'text_delta':
        resetStreamTimeout();
        set((state) => ({
          streamingContent: state.streamingContent + (chunk.content || '')
        }));
        break;

      case 'thinking_delta':
        resetStreamTimeout();
        set((state) => ({
          streamingThinking: state.streamingThinking + (chunk.content || '')
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
            status: 'executing' as const
          }]
        }));
        break;

      case 'tool_result':
        resetStreamTimeout();
        set((state) => ({
          streamingToolCalls: state.streamingToolCalls.map(tc =>
            tc.toolCallId === chunk.toolCallId
              ? { ...tc, status: 'done' as const, result: chunk.toolResult, render: chunk.render, durationMs: chunk.durationMs }
              : tc
          )
        }));
        break;

      case 'done':
        clearStreamTimeout();

        const finalContent = get().streamingContent.trim();
        const finalThinking = get().streamingThinking.trim();
        const doneTools = get().streamingToolCalls;

        // 构建 ThinkingChainEntry[] 匹配 API 格式
        const thinkingChain: ThinkingChainEntry[] = [];
        let seq = 0;

        if (finalThinking) {
          thinkingChain.push({
            id: 0, sequenceNum: seq++, type: 'thinking',
            content: finalThinking, toolCallId: null, toolName: null, toolInput: null, durationMs: null
          });
        }

        for (const tc of doneTools) {
          thinkingChain.push({
            id: 0, sequenceNum: seq++, type: 'tool_call',
            content: null, toolCallId: tc.toolCallId, toolName: tc.toolName,
            toolInput: safeParseJSON(tc.toolInput), durationMs: null,
            toolDisplayName: tc.toolDisplayName, extensionCode: tc.extensionCode,
            extensionName: tc.extensionName, toolKind: tc.toolKind, skillName: tc.skillName
          });
          if (tc.status === 'done') {
            thinkingChain.push({
              id: 0, sequenceNum: seq++, type: 'tool_result',
              content: tc.result || null, toolCallId: tc.toolCallId, toolName: tc.toolName,
              toolInput: null, durationMs: tc.durationMs || null,
              toolDisplayName: tc.toolDisplayName, extensionCode: tc.extensionCode,
              extensionName: tc.extensionName, toolKind: tc.toolKind, skillName: tc.skillName,
              renderJson: tc.render || null
            });
          }
        }

        if (currentConversation) {
          const assistantMsg: Message = {
            id: Date.now(),
            role: 'assistant' as const,
            content: finalContent || null,
            reasoningContent: finalThinking || null,
            toolCallId: null,
            toolName: null,
            toolInput: null,
            toolResult: null,
            thinkingChain: thinkingChain.length > 0 ? thinkingChain : null,
            sequenceNum: get().messages.length + 1,
            createdAt: new Date().toISOString()
          };

          if (finalContent || thinkingChain.length > 0) {
            set((state) => ({
              messages: [...state.messages, assistantMsg],
              isStreaming: false,
              streamingContent: '',
              streamingThinking: '',
              streamingToolCalls: []
            }));
          } else {
            set({ isStreaming: false, streamingContent: '', streamingThinking: '', streamingToolCalls: [] });
          }
        } else {
          set({ isStreaming: false, streamingContent: '', streamingThinking: '', streamingToolCalls: [] });
        }

        get().loadConversations();
        break;

      case 'error':
        clearStreamTimeout();
        set({ isStreaming: false, error: chunk.content || '未知错误' });
        break;

      case 'title_updated':
        if (chunk.title && currentConversation) {
          set((state) => ({
            currentConversation: state.currentConversation
              ? { ...state.currentConversation, title: chunk.title! }
              : null
          }));
          get().loadConversations();
        }
        break;

      case 'thinking':
      case 'message_saved':
        break;
    }
  },

  clearError: () => set({ error: null })
}));

function safeParseJSON(json: string): Record<string, unknown> | null {
  try {
    return JSON.parse(json);
  } catch {
    return null;
  }
}
