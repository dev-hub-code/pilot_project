/**
 * A table's rows as stacked cards, for phones: shown below the `sm` breakpoint, where the page hides its
 * DataTable (`className="hidden sm:block"`), so a row reads top to bottom instead of scrolling sideways.
 */
export function CardList({ label, children }: { label: string; children: React.ReactNode }) {
  return <ul aria-label={label} className="divide-y divide-border overflow-hidden rounded-2xl border border-border bg-surface shadow-sm sm:hidden">{children}</ul>;
}

export function CardListItem({ children }: { children: React.ReactNode }) {
  return <li className="space-y-3 p-4 text-sm transition-colors active:bg-background">{children}</li>;
}

/** One labelled value of a card; place inside a `<dl>`. */
export function CardField({ label, children, className = "" }: { label: string; children: React.ReactNode; className?: string }) {
  return (
    <div className={`min-w-0 ${className}`}>
      <dt className="text-[11px] uppercase tracking-[0.08em] text-muted">{label}</dt>
      <dd className="mt-0.5">{children}</dd>
    </div>
  );
}
