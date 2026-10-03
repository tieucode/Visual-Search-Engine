import { forwardRef, type ButtonHTMLAttributes } from 'react';

export type ButtonVariant = 'primary' | 'ghost' | 'outline' | 'destructive';
export type ButtonSize = 'default' | 'large';

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  loading?: boolean;
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = 'primary', size = 'default', loading = false, className = '', disabled, children, type = 'button', ...props },
  ref,
) {
  return (
    <button
      {...props}
      ref={ref}
      type={type}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={`button button--${variant} ${size === 'large' ? 'button--large' : ''} ${className}`.trim()}
    >
      {children}
    </button>
  );
});
