import { Client } from '@stomp/stompjs';
import type { IMessage, StompSubscription } from '@stomp/stompjs';
import type { StreamingChunk } from '../types';

/** 获取存储的 JWT token */
function getToken(): string | null {
  const stored = localStorage.getItem('auth-storage');
  if (stored) {
    try {
      const parsed = JSON.parse(stored);
      return parsed?.state?.token || null;
    } catch {
      return null;
    }
  }
  return null;
}

/** 创建 STOMP 客户端 */
export function createStompClient(): Client {
  const token = getToken();

  // 与当前页面同源，开发环境由 Vite 代理 /ws，生产环境由后端直接接管
  const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  const brokerURL = `${wsProtocol}//${window.location.host}/ws/chat`;

  const client = new Client({
    brokerURL,
    connectHeaders: {
      Authorization: token ? `Bearer ${token}` : ''
    },
    debug: (str) => {
      console.debug('STOMP:', str);
    },
    reconnectDelay: 5000, // 自动重连
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000
  });

  return client;
}

/**
 * WebSocket 管理器 - 单例管理 STOMP 连接
 */
class WebSocketManager {
  private client: Client | null = null;
  private subscriptions: Map<string, StompSubscription> = new Map();
  // 待处理的订阅 (WebSocket 未连接时暂存)
  private pendingSubscription: { conversationId: number; onMessage: (chunk: StreamingChunk) => void } | null = null;

  /** 建立连接 */
  connect(): void {
    if (this.client?.active) return;

    this.client = createStompClient();

    this.client.onConnect = () => {
      console.log('WebSocket 已连接');
      // 连接成功后，应用之前暂存的订阅
      this.applyPendingSubscription();
    };

    this.client.onDisconnect = () => {
      console.log('WebSocket 已断开');
    };

    this.client.onStompError = (frame) => {
      console.error('STOMP 错误:', frame.headers['message']);
    };

    this.client.activate();
  }

  /** 订阅对话消息 - 如果尚未连接则暂存，连接后自动应用 */
  subscribe(conversationId: number, onMessage: (chunk: StreamingChunk) => void): void {
    this.pendingSubscription = { conversationId, onMessage };

    if (!this.client?.active) {
      console.log('WebSocket 未连接，订阅已暂存，连接后将自动应用');
      return;
    }

    this.applyPendingSubscription();
  }

  /** 实际执行订阅 */
  private applyPendingSubscription(): void {
    if (!this.pendingSubscription || !this.client?.active) return;

    const { conversationId, onMessage } = this.pendingSubscription;
    const destination = `/user/queue/messages/${conversationId}`;

    // 取消之前的订阅
    this.unsubscribeAll();

    const sub = this.client.subscribe(destination, (message: IMessage) => {
      try {
        const chunk: StreamingChunk = JSON.parse(message.body);
        onMessage(chunk);
      } catch (e) {
        console.error('解析 WebSocket 消息失败:', e);
      }
    });

    this.subscriptions.set(`conv-${conversationId}`, sub);
    console.log('已订阅对话消息:', conversationId);
  }

  /** 发送消息 */
  sendMessage(conversationId: number, content: string): void {
    if (!this.client?.active) {
      console.warn('WebSocket 未连接，无法发送消息');
      return;
    }

    console.log('发送 STOMP 消息: 对话ID=', conversationId, '内容=', content);
    this.client.publish({
      destination: `/app/chat/${conversationId}`,
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ content })
    });
  }

  /** 取消所有订阅 */
  private unsubscribeAll(): void {
    this.subscriptions.forEach((sub) => sub.unsubscribe());
    this.subscriptions.clear();
  }

  /** 断开连接 */
  disconnect(): void {
    this.unsubscribeAll();
    this.pendingSubscription = null;
    this.client?.deactivate();
    this.client = null;
  }
}

// 导出单例
export const wsManager = new WebSocketManager();
