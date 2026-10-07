import type { ButtonHTMLAttributes } from "react";

/**
 * primary   — solid gold, square corners (the main call to action)
 * secondary — outlined pill (header actions, secondary choices)
 * quiet     — bordered square, for low-emphasis actions inside cards
 * danger    — destructive decisions
 */
export const buttonVariants = {
  primary: "rounded-none bg-gold text-on-gold hover:bg-gold-hover",
  secondary: "rounded-full border border-foreground/80 bg-transparent text-foreground hover:bg-foreground hover:text-surface",
  quiet: "rounded-none border border-border bg-surface text-foreground hover:border-foreground/40",
  danger: "rounded-none bg-rose-700 text-white hover:bg-rose-800",
} as const;

export const buttonBase =
  "inline-flex h-11 items-center justify-center gap-2 px-5 text-sm font-medium tracking-wide transition-colors disabled:cursor-not-allowed disabled:opacity-60";

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: keyof typeof buttonVariants;
}

export function Button({ variant = "primary", className = "", ...props }: ButtonProps) {
  return <button className={`${buttonBase} ${buttonVariants[variant]} ${className}`} {...props} />;
}
