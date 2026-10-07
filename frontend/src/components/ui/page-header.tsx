export function PageHeader({ title, description, actions }: { title: string; description?: string; actions?: React.ReactNode }) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-4">
      <div className="space-y-1">
        <h1 className="text-3xl font-semibold tracking-tight sm:text-4xl">{title}</h1>
        {description && <p className="text-sm text-muted">{description}</p>}
      </div>
      {actions}
    </div>
  );
}
