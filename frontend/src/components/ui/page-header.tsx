/**
 * A page's title block: a soft card with an accent edge and a faint glow, the title and description on the left and
 * the page's main actions on the right.
 */
export function PageHeader({ title, description, actions }: { title: string; description?: string; actions?: React.ReactNode }) {
  return (
    <div className="relative isolate overflow-hidden rounded-2xl border border-border bg-[linear-gradient(120deg,var(--surface)_0%,var(--surface)_55%,color-mix(in_oklab,var(--gold)_7%,var(--surface))_100%)] px-5 py-5 shadow-sm sm:px-7 sm:py-6">
      <span aria-hidden="true" className="absolute inset-y-0 left-0 w-1 bg-gradient-to-b from-gold to-gold-on-ink" />
      <span aria-hidden="true" className="absolute -top-16 -right-10 -z-10 size-48 rounded-full bg-gold/10 blur-3xl" />
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="min-w-0 space-y-1">
          <h1 className="text-2xl font-semibold tracking-tight text-balance sm:text-3xl">{title}</h1>
          {description && <p className="max-w-3xl text-sm leading-relaxed text-muted">{description}</p>}
        </div>
        {actions}
      </div>
    </div>
  );
}
