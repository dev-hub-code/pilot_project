const tones = {
  success: "border-emerald-200 bg-emerald-50 text-emerald-800 dark:border-emerald-500/30 dark:bg-emerald-500/10 dark:text-emerald-300",
  info: "border-sky-200 bg-sky-50 text-sky-800 dark:border-sky-500/30 dark:bg-sky-500/10 dark:text-sky-300",
  warning: "border-amber-200 bg-amber-50 text-amber-800 dark:border-amber-500/30 dark:bg-amber-500/10 dark:text-amber-300",
} as const;

export function Notice({ tone = "info", children }: { tone?: keyof typeof tones; children: React.ReactNode }) {
  return (
    <div role="status" className={`rounded-none border-l-4 px-3 py-2 text-sm ${tones[tone]}`}>
      {children}
    </div>
  );
}
