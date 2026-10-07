export function Alert({ children }: { children: React.ReactNode }) {
  return (
    <div
      role="alert"
      className="rounded-none border-l-4 border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700 dark:border-rose-500/30 dark:bg-rose-500/10 dark:text-rose-300"
    >
      {children}
    </div>
  );
}
