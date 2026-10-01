import type { HTMLAttributes } from 'react';

export interface CardProps extends HTMLAttributes<HTMLElement> {
  as?: 'article' | 'div' | 'section';
  interactive?: boolean;
}

export function Card({ as: Element = 'div', interactive = false, className = '', ...props }: CardProps) {
  return <Element {...props} className={`card ${interactive ? 'card--interactive' : ''} ${className}`.trim()} />;
}
