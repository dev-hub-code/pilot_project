export function EmptyState({ title, description }: { title: string; description?: string }) {
  return (
    <div className="border border-dashed border-border px-6 py-10 text-center">
      <p className="font-medium">{title}</p>
      {description && <p className="mt-1 text-sm text-muted">{description}</p>}
    </div>
  );
}
