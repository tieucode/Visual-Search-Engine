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
  const dialogRef = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const previousOverflow = document.body.style.overflow;
    const previousFocus = document.activeElement as HTMLElement | null;
    document.body.style.overflow = 'hidden';
    const dialog = dialogRef.current;
    dialog?.showModal();
    return () => {
      dialog?.close();
      document.body.style.overflow = previousOverflow;
      previousFocus?.focus?.();
    };
  }, []);

  return createPortal(
    <dialog
      ref={dialogRef}
      className="modal-overlay"
      aria-label={label}
      onCancel={(event) => { event.preventDefault(); onClose?.(); }}
      onMouseDown={(event) => {
        if (onClose && event.target === event.currentTarget) onClose();
      }}
    >
      <div className={`modal modal--${size}`}>
        {(title || onClose) && (
          <header className="modal__header">
            <h2 className="modal__title">{title}</h2>
            <div className="modal__actions">
              {actions}
              {onClose && (
                <button type="button" className="icon-button icon-button--sm" onClick={onClose} aria-label="Đóng">
                  <X size={18} />
                </button>
              )}
            </div>
          </header>
        )}
        {children}
      </div>
    </dialog>,
    document.body,
  );
}
