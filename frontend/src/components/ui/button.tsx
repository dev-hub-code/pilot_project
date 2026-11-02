import type { ButtonHTMLAttributes } from "react";

/**
 * primary   — solid accent (the main call to action)
 * secondary — outlined (header actions, secondary choices)
 * quiet     — bordered, for low-emphasis actions inside cards
 * danger    — destructive decisions
 */
export const buttonVariants = {
  primary: "rounded-lg bg-gold text-on-gold hover:bg-gold-hover",
  secondary: "rounded-lg border border-border bg-surface text-foreground hover:border-gold hover:text-gold-text",
  quiet: "rounded-lg border border-border bg-surface text-foreground hover:border-foreground/40",
  danger: "rounded-lg bg-rose-700 text-white hover:bg-rose-800",
} as const;

export const buttonBase =
  "inline-flex min-h-11 items-center justify-center gap-2 px-5 py-2 text-center text-sm font-medium tracking-wide transition-colors disabled:cursor-not-allowed disabled:opacity-60";

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: keyof typeof buttonVariants;
}

export function Button({ variant = "primary", className = "", ...props }: ButtonProps) {
  return <button className={`${buttonBase} ${buttonVariants[variant]} ${className}`} {...props} />;
}
