import type {
  ButtonHTMLAttributes,
  InputHTMLAttributes,
  ReactNode,
  SelectHTMLAttributes,
  TextareaHTMLAttributes,
} from 'react';
import { useEffect } from 'react';
import { X } from 'lucide-react';

/**
 * 后台 UI 基础组件：白底 SaaS 风格。
 * 设计基调：白底、细边框、中性色 + indigo 强调色、弱阴影、紧凑间距。
 */

type Tone = 'gray' | 'green' | 'red' | 'amber' | 'blue' | 'indigo';

const BADGE_TONES: Record<Tone, string> = {
  gray: 'bg-gray-100 text-gray-600',
  green: 'bg-emerald-50 text-emerald-700',
  red: 'bg-red-50 text-red-600',
  amber: 'bg-amber-50 text-amber-700',
  blue: 'bg-sky-50 text-sky-700',
  indigo: 'bg-indigo-50 text-indigo-700',
};

export function Badge({ tone = 'gray', children, className = '' }: { tone?: Tone; children: ReactNode; className?: string }) {
  return (
    <span className={`inline-flex items-center gap-1 rounded-md px-2 py-0.5 text-[11px] font-medium ${BADGE_TONES[tone]} ${className}`}>
      {children}
    </span>
  );
}

export function PageHeader({ title, description, actions }: {
  title: string;
  description?: string;
  actions?: ReactNode;
}) {
  return (
    <div className="mb-6 flex items-start justify-between gap-4 border-b border-gray-100 pb-5">
      <div className="min-w-0">
        <h1 className="text-[22px] font-semibold leading-tight tracking-tight text-gray-900">{title}</h1>
        {description && <p className="mt-1.5 text-sm leading-relaxed text-gray-500">{description}</p>}
      </div>
      {actions && <div className="flex shrink-0 items-center gap-2">{actions}</div>}
    </div>
  );
}

export function Card({ title, description, actions, children, className = '', flush = false }: {
  title?: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
  flush?: boolean;
}) {
  return (
    <section className={`overflow-hidden rounded-xl border border-gray-200 bg-white ${className}`}>
      {(title || actions) && (
        <div className="flex items-start justify-between gap-3 border-b border-gray-100 px-5 py-4">
          <div className="min-w-0">
            {title && <h2 className="text-sm font-semibold text-gray-900">{title}</h2>}
            {description && <p className="mt-0.5 text-xs leading-relaxed text-gray-500">{description}</p>}
          </div>
          {actions && <div className="flex shrink-0 items-center gap-2">{actions}</div>}
        </div>
      )}
      <div className={flush ? '' : 'p-5'}>{children}</div>
    </section>
  );
}

type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger';

const BUTTON_VARIANTS: Record<ButtonVariant, string> = {
  primary: 'bg-gray-900 text-white hover:bg-gray-800 active:bg-black',
  secondary: 'border border-gray-200 bg-white text-gray-700 hover:bg-gray-50 active:bg-gray-100',
  ghost: 'text-gray-600 hover:bg-gray-100 hover:text-gray-900',
  danger: 'text-red-600 hover:bg-red-50',
};

export function Button({ variant = 'secondary', size = 'md', className = '', children, ...props }: {
  variant?: ButtonVariant;
  size?: 'sm' | 'md';
} & ButtonHTMLAttributes<HTMLButtonElement>) {
  const sizeClass = size === 'sm' ? 'h-8 px-2.5 text-[13px]' : 'h-9 px-3.5 text-sm';
  return (
    <button
      {...props}
      className={`inline-flex items-center justify-center gap-1.5 rounded-lg font-medium transition-colors disabled:pointer-events-none disabled:opacity-50 cursor-pointer ${BUTTON_VARIANTS[variant]} ${sizeClass} ${className}`}
    >
      {children}
    </button>
  );
}

export function IconButton({ tone = 'default', className = '', children, ...props }: {
  tone?: 'default' | 'danger';
} & ButtonHTMLAttributes<HTMLButtonElement>) {
  const toneClass = tone === 'danger'
    ? 'text-gray-400 hover:bg-red-50 hover:text-red-600'
    : 'text-gray-400 hover:bg-gray-100 hover:text-gray-700';
  return (
    <button
      {...props}
      className={`inline-flex h-8 w-8 items-center justify-center rounded-lg transition-colors disabled:opacity-40 cursor-pointer ${toneClass} ${className}`}
    >
      {children}
    </button>
  );
}

export function Field({ label, hint, required, children, className = '' }: {
  label?: ReactNode;
  hint?: ReactNode;
  required?: boolean;
  children: ReactNode;
  className?: string;
}) {
  return (
    <label className={`flex flex-col gap-1.5 ${className}`}>
      {label && (
        <span className="text-xs font-medium text-gray-600">
          {label}
          {required && <span className="ml-0.5 text-red-500">*</span>}
        </span>
      )}
      {children}
      {hint && <span className="text-[11px] leading-relaxed text-gray-400">{hint}</span>}
    </label>
  );
}

const CONTROL_BASE = 'w-full rounded-lg border border-gray-200 bg-white text-sm text-gray-900 placeholder:text-gray-400 outline-none transition focus:border-gray-400 focus:ring-4 focus:ring-gray-100 disabled:bg-gray-50 disabled:text-gray-400';

export function Input({ className = '', ...props }: InputHTMLAttributes<HTMLInputElement>) {
  return <input {...props} className={`${CONTROL_BASE} h-9 px-3 ${className}`} />;
}

export function Select({ className = '', children, ...props }: SelectHTMLAttributes<HTMLSelectElement>) {
  return <select {...props} className={`${CONTROL_BASE} h-9 px-2.5 ${className}`}>{children}</select>;
}

export function Textarea({ className = '', ...props }: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return <textarea {...props} className={`${CONTROL_BASE} px-3 py-2 leading-relaxed ${className}`} />;
}

export function Alert({ tone = 'error', children, onClose }: {
  tone?: 'error' | 'warning' | 'info' | 'success';
  children: ReactNode;
  onClose?: () => void;
}) {
  const tones = {
    error: 'border-red-200 bg-red-50 text-red-700',
    warning: 'border-amber-200 bg-amber-50 text-amber-700',
    info: 'border-sky-200 bg-sky-50 text-sky-700',
    success: 'border-emerald-200 bg-emerald-50 text-emerald-700',
  };
  return (
    <div className={`mb-4 flex items-start justify-between gap-3 rounded-lg border px-3.5 py-2.5 text-sm ${tones[tone]}`}>
      <div className="min-w-0 whitespace-pre-wrap break-words">{children}</div>
      {onClose && (
        <button onClick={onClose} className="mt-0.5 shrink-0 opacity-60 hover:opacity-100 cursor-pointer">
          <X size={14} />
        </button>
      )}
    </div>
  );
}

export function EmptyState({ icon, title, description, action }: {
  icon?: ReactNode;
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-gray-200 bg-white px-6 py-14 text-center">
      {icon && <div className="mb-3 flex h-11 w-11 items-center justify-center rounded-xl bg-gray-50 text-gray-400">{icon}</div>}
      <div className="text-sm font-medium text-gray-700">{title}</div>
      {description && <div className="mt-1 max-w-md text-xs leading-relaxed text-gray-400">{description}</div>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}

export function Modal({ title, description, onClose, children, footer, width = 'max-w-2xl' }: {
  title: ReactNode;
  description?: ReactNode;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
  width?: string;
}) {
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [onClose]);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-gray-900/30 p-4 backdrop-blur-[1px]">
      <div className={`flex max-h-[90vh] w-full flex-col overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-xl ${width}`}>
        <div className="flex items-start justify-between gap-4 border-b border-gray-100 px-6 py-4">
          <div className="min-w-0">
            <h3 className="text-base font-semibold text-gray-900">{title}</h3>
            {description && <p className="mt-0.5 text-xs text-gray-500">{description}</p>}
          </div>
          <IconButton onClick={onClose} aria-label="关闭"><X size={16} /></IconButton>
        </div>
        <div className="flex-1 overflow-y-auto px-6 py-5">{children}</div>
        {footer && <div className="flex justify-end gap-2 border-t border-gray-100 px-6 py-3.5">{footer}</div>}
      </div>
    </div>
  );
}

export function Stat({ label, value, hint }: { label: string; value: ReactNode; hint?: ReactNode }) {
  return (
    <div className="rounded-xl border border-gray-200 bg-white px-4 py-3.5">
      <div className="text-xs text-gray-500">{label}</div>
      <div className="mt-1 truncate text-xl font-semibold tracking-tight text-gray-900">{value}</div>
      {hint && <div className="mt-0.5 truncate text-[11px] text-gray-400">{hint}</div>}
    </div>
  );
}

/** 列表行容器：白底卡片内的分隔行 */
export function ListRow({ children, onClick, active = false }: {
  children: ReactNode;
  onClick?: () => void;
  active?: boolean;
}) {
  const base = 'flex items-start gap-3 px-5 py-4 transition-colors';
  if (!onClick) return <div className={base}>{children}</div>;
  return (
    <button
      onClick={onClick}
      className={`${base} w-full text-left cursor-pointer ${active ? 'bg-indigo-50/60' : 'hover:bg-gray-50'}`}
    >
      {children}
    </button>
  );
}
