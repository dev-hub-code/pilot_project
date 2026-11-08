import { Inbox } from "lucide-react";

export function EmptyState({ title, description }: { title: string; description?: string }) {
  return (
    <div className="flex flex-col items-center rounded-2xl border border-dashed border-border bg-background/40 px-6 py-10 text-center">
      <span aria-hidden="true" className="mb-3 flex size-11 items-center justify-center rounded-full bg-gold/10 text-gold-text">
        <Inbox className="size-5" />
      </span>
      <p className="font-medium">{title}</p>
      {description && <p className="mt-1 max-w-md text-sm text-muted">{description}</p>}
    </div>
  );
}
