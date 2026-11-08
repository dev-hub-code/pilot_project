import type { LucideIcon } from "lucide-react";

/** A labelled value with an icon, for detail grids (inside a <dl>). */
export function DetailTile({ icon: Icon, label, children, className = "" }: {
  icon: LucideIcon;
  label: string;
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <div className={`flex min-w-0 gap-3 rounded-xl bg-background/60 p-3 ${className}`}>
      <span aria-hidden="true" className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-surface text-gold-text shadow-sm">
        <Icon className="size-4" />
      </span>
      <div className="min-w-0">
        <dt className="text-xs text-muted">{label}</dt>
        <dd className="mt-0.5 font-medium break-words">{children}</dd>
      </div>
    </div>
  );
}
