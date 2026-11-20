import { useEffect, useState } from 'react';
import { AlertTriangle, CheckCircle2, Info, X, XCircle } from 'lucide-react';
import { Button, Modal } from '../admin/ui';

/**
 * 全局反馈：Toast 与 Promise 式确认框。
 * 挂载一次 <Toaster /> 与 <ConfirmHost /> 即可在任意页面调用 toast / confirmDialog。
 */

type ToastTone = 'success' | 'error' | 'info' | 'warning';

interface ToastItem {
  id: number;
  tone: ToastTone;
  message: string;
}

let toastItems: ToastItem[] = [];
const toastListeners = new Set<(items: ToastItem[]) => void>();
let toastSeq = 0;

function emitToasts() {
  const snapshot = [...toastItems];
  toastListeners.forEach((listener) => listener(snapshot));
}

export function pushToast(tone: ToastTone, message: string, duration = 3200) {
  const id = ++toastSeq;
  toastItems = [...toastItems, { id, tone, message }];
  emitToasts();
  window.setTimeout(() => {
    toastItems = toastItems.filter((item) => item.id !== id);
    emitToasts();
  }, duration);
}

export const toast = {
  success: (message: string) => pushToast('success', message),
  error: (message: string) => pushToast('error', message),
  info: (message: string) => pushToast('info', message),
  warning: (message: string) => pushToast('warning', message),
};

const TONE_STYLE: Record<ToastTone, string> = {
  success: 'border-emerald-200 bg-white text-emerald-700',
  error: 'border-red-200 bg-white text-red-700',
  info: 'border-sky-200 bg-white text-sky-700',
  warning: 'border-amber-200 bg-white text-amber-700',
};

function ToneIcon({ tone }: { tone: ToastTone }) {
  const cls = 'shrink-0';
  if (tone === 'success') return <CheckCircle2 size={16} className={`${cls} text-emerald-500`} />;
  if (tone === 'error') return <XCircle size={16} className={`${cls} text-red-500`} />;
  if (tone === 'warning') return <AlertTriangle size={16} className={`${cls} text-amber-500`} />;
  return <Info size={16} className={`${cls} text-sky-500`} />;
}

export function Toaster() {
  const [items, setItems] = useState<ToastItem[]>([]);

  useEffect(() => {
    toastListeners.add(setItems);
    setItems([...toastItems]);
    return () => {
      toastListeners.delete(setItems);
    };
  }, []);

  return (
    <div className="pointer-events-none fixed right-4 top-4 z-[100] flex w-80 flex-col gap-2">
      {items.map((item) => (
        <div
          key={item.id}
          className={`pointer-events-auto flex items-start gap-2 rounded-xl border px-3.5 py-2.5 text-sm shadow-lg ${TONE_STYLE[item.tone]}`}
        >
          <ToneIcon tone={item.tone} />
          <span className="min-w-0 flex-1 leading-relaxed text-gray-700">{item.message}</span>
          <button
            onClick={() => {
              toastItems = toastItems.filter((t) => t.id !== item.id);
              emitToasts();
            }}
            className="mt-0.5 shrink-0 text-gray-300 hover:text-gray-500 cursor-pointer"
            aria-label="关闭"
          >
            <X size={13} />
          </button>
        </div>
      ))}
    </div>
  );
}

interface ConfirmOptions {
  title: string;
  description?: string;
  confirmText?: string;
  cancelText?: string;
  danger?: boolean;
}

interface PendingConfirm {
  options: ConfirmOptions;
  resolve: (value: boolean) => void;
}

let pendingConfirm: PendingConfirm | null = null;
const confirmListeners = new Set<() => void>();

function emitConfirm() {
  confirmListeners.forEach((listener) => listener());
}

/** Promise 式确认框，替代 window.confirm */
export function confirmDialog(options: ConfirmOptions): Promise<boolean> {
  return new Promise((resolve) => {
    pendingConfirm = { options, resolve };
    emitConfirm();
  });
}

export function ConfirmHost() {
  const [, force] = useState(0);

  useEffect(() => {
    const listener = () => force((v) => v + 1);
    confirmListeners.add(listener);
    return () => {
      confirmListeners.delete(listener);
    };
  }, []);

  if (!pendingConfirm) return null;
  const { options } = pendingConfirm;

  const close = (value: boolean) => {
    const current = pendingConfirm;
    pendingConfirm = null;
    emitConfirm();
    current?.resolve(value);
  };

  return (
    <Modal
      title={options.title}
      description={options.description}
      onClose={() => close(false)}
      width="max-w-md"
      footer={
        <>
          <Button onClick={() => close(false)}>{options.cancelText || '取消'}</Button>
          <Button
            variant={options.danger ? 'danger' : 'primary'}
            className={options.danger ? 'border border-red-200 bg-red-600 text-white hover:bg-red-700' : undefined}
            onClick={() => close(true)}
          >
            {options.confirmText || '确认'}
          </Button>
        </>
      }
    >
      <p className="text-sm leading-relaxed text-gray-600">
        {options.description || '该操作不可撤销，请确认是否继续。'}
      </p>
    </Modal>
  );
}
