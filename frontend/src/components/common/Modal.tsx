import { useEffect, useRef, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { X } from 'lucide-react';
import '../../styles/layout.css';

export interface ModalProps {
  /** Accessible name of the dialog. */
  label: string;
  /** Omit to make the modal non-dismissable (no close button, Esc or outside click). */
  onClose?: () => void;
  size?: 'md' | 'sm' | 'static';
  title?: ReactNode;
  actions?: ReactNode;
  children: ReactNode;
}

export function Modal({ label, onClose, size = 'md', title, actions, children }: ModalProps) {
  const panelRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const previousOverflow = document.body.style.overflow;
    const previousFocus = document.activeElement as HTMLElement | null;
    document.body.style.overflow = 'hidden';
    panelRef.current?.focus();
    return () => {
      document.body.style.overflow = previousOverflow;
      previousFocus?.focus?.();
    };
  }, []);

  useEffect(() => {
    if (!onClose) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [onClose]);

  return createPortal(
    <div
      className="modal-overlay"
      onMouseDown={(event) => {
        if (onClose && event.target === event.currentTarget) onClose();
      }}
    >
      <div ref={panelRef} className={`modal modal--${size}`} role="dialog" aria-modal="true" aria-label={label} tabIndex={-1}>
        {(title || onClose) && (
          <header className="modal__header">
            <h2 className="modal__title">{title}</h2>
            <div className="modal__actions">
              {actions}
              {onClose && (
                <button type="button" className="icon-button icon-button--sm" onClick={onClose} aria-label="Close">
                  <X size={18} />
                </button>
              )}
            </div>
          </header>
        )}
        {children}
      </div>
    </div>,
    document.body,
  );
}
