import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import { AlertTriangle, CheckCircle2, XCircle } from 'lucide-react';
import '../../styles/layout.css';

export type ToastType = 'success' | 'warning' | 'error';

interface ToastItem {
  id: number;
  type: ToastType;
  message: string;
}

const DURATION: Record<ToastType, number> = { success: 3000, warning: 4000, error: 5000 };
const ICONS = { success: CheckCircle2, warning: AlertTriangle, error: XCircle } as const;

const ToastContext = createContext<((type: ToastType, message: string) => void) | null>(null);
let sequence = 0;

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);

  const push = useCallback((type: ToastType, message: string) => {
    const id = ++sequence;
    setToasts((current) => [...current.slice(-3), { id, type, message }]);
    window.setTimeout(() => setToasts((current) => current.filter((toast) => toast.id !== id)), DURATION[type]);
  }, []);

  const value = useMemo(() => push, [push]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="toast-stack" aria-live="polite">
        {toasts.map(({ id, type, message }) => {
          const Icon = ICONS[type];
          return (
            <div key={id} className={`toast toast--${type}`} role={type === 'error' ? 'alert' : 'status'}>
              <Icon size={16} aria-hidden="true" />
              <span>{message}</span>
            </div>
          );
        })}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const push = useContext(ToastContext);
  if (!push) throw new Error('useToast must be used inside ToastProvider');
  return push;
}
