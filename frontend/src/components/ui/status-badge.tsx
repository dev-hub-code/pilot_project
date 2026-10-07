const tones = {
  success: "bg-emerald-50 text-emerald-700 ring-emerald-600/20 dark:bg-emerald-500/10 dark:text-emerald-400",
  warning: "bg-amber-50 text-amber-700 ring-amber-600/20 dark:bg-amber-500/10 dark:text-amber-400",
  danger: "bg-rose-50 text-rose-700 ring-rose-600/20 dark:bg-rose-500/10 dark:text-rose-400",
  neutral: "bg-slate-50 text-slate-700 ring-slate-600/20 dark:bg-slate-500/10 dark:text-slate-300",
} as const;

export type StatusTone = keyof typeof tones;

interface StatusBadgeProps {
  tone: StatusTone;
  children: React.ReactNode;
}

export function StatusBadge({ tone, children }: StatusBadgeProps) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset ${tones[tone]}`}
    >
      <span aria-hidden className="size-1.5 rounded-full bg-current" />
      {children}
    </span>
  );
}
