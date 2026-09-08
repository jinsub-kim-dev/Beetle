import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

/**
 * Tailwind 클래스를 병합한다. shadcn/ui 컴포넌트의 표준 유틸리티다.
 */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs))
}
