import { forwardRef, useId, type InputHTMLAttributes } from 'react';

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
}

export const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  { id, label, error, className = '', 'aria-describedby': describedBy, ...props },
  ref,
) {
  const generatedId = useId();
  const inputId = id ?? generatedId;
  const errorId = error ? `${inputId}-error` : undefined;
  return (
    <div>
      {label && <label className="field-label" htmlFor={inputId}>{label}</label>}
      <input
        {...props}
        id={inputId}
        ref={ref}
        className={`input ${className}`.trim()}
        aria-invalid={Boolean(error) || undefined}
        aria-describedby={[describedBy, errorId].filter(Boolean).join(' ') || undefined}
      />
      {error && <p id={errorId} className="field-error" role="alert">{error}</p>}
    </div>
  );
});
