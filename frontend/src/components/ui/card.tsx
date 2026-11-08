interface CardProps {
  title?: string;
  description?: string;
  actions?: React.ReactNode;
  children: React.ReactNode;
  className?: string;
}

/** A titled panel: the heading sits on its own band above the content. */
export function Card({ title, description, actions, children, className = "" }: CardProps) {
  const headed = Boolean(title || actions);
  return (
    <section className={`overflow-hidden rounded-2xl border border-border bg-surface shadow-sm transition-shadow duration-300 hover:shadow-md ${className}`}>
      {headed && (
        <header className="flex flex-wrap items-start justify-between gap-3 border-b border-border/70 bg-background/40 px-5 py-4 sm:px-6">
          <div className="min-w-0">
            {title && (
              <h2 className="flex items-center gap-2.5 text-base font-semibold tracking-tight sm:text-lg">
                <span aria-hidden="true" className="h-4 w-1 rounded-full bg-gold" />
                {title}
              </h2>
            )}
            {description && <p className="mt-0.5 text-sm text-muted">{description}</p>}
          </div>
          {actions}
        </header>
      )}
      <div className={headed ? "px-5 py-5 sm:px-6" : "p-5 sm:p-6"}>{children}</div>
    </section>
  );
}
