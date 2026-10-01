import type { HTMLAttributes } from 'react';

export type BadgeVariant = 'default' | 'brand' | 'success' | 'warning' | 'muted';

export interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  variant?: BadgeVariant;
}

export function Badge({ variant = 'default', className = '', ...props }: BadgeProps) {
  return <span {...props} className={`badge ${variant === 'default' ? '' : `badge--${variant}`} ${className}`.trim()} />;
}
