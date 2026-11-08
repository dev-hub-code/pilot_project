import Link from "next/link";
import { ArrowUpRight, type LucideIcon } from "lucide-react";

export interface StatTile {
  label: string;
  value: React.ReactNode;
  note?: React.ReactNode;
  icon: LucideIcon;
  /** The page with the detail behind the figure. */
  href?: string;
}

/** The dashboard's headline figures: each with an icon, and a link through to its page. */
export function StatTiles({ tiles, label }: { tiles: readonly StatTile[]; label: string }) {
  return (
    <section aria-label={label} className={`grid gap-4 ${tiles.length === 4 ? "sm:grid-cols-2 xl:grid-cols-4" : "sm:grid-cols-3"}`}>
      {tiles.map(({ label: name, value, note, icon: Icon, href }) => {
        const body = (
          <>
            <div className="flex items-start justify-between gap-3">
              <span aria-hidden="true" className="flex size-11 items-center justify-center rounded-xl bg-gold/10 text-gold-text transition-colors duration-300 group-hover:bg-gold group-hover:text-on-gold">
                <Icon className="size-5" />
              </span>
              {href && <ArrowUpRight aria-hidden="true" className="size-4 text-muted transition duration-300 group-hover:translate-x-0.5 group-hover:-translate-y-0.5 group-hover:text-gold-text" />}
            </div>
            <p className="mt-5 text-sm text-muted">{name}</p>
            <p className="mt-1 font-display text-2xl font-semibold tracking-tight tabular-nums sm:text-3xl">{value}</p>
            {note && <p className="mt-1.5 text-xs text-muted">{note}</p>}
          </>
        );
        const className = "group block h-full rounded-2xl border border-border bg-surface p-5 shadow-sm transition duration-300 sm:p-6";
        return (
          <div key={name}>
            {href ? (
              <Link href={href} className={`${className} hover:-translate-y-0.5 hover:border-gold/40 hover:shadow-lg hover:shadow-gold/10`}>{body}</Link>
            ) : (
              <div className={className}>{body}</div>
            )}
          </div>
        );
      })}
    </section>
  );
}

export interface Shortcut {
  href: string;
  label: string;
  detail: string;
  icon: LucideIcon;
}

/** Quick links to the pages investors open most. */
export function Shortcuts({ links }: { links: readonly Shortcut[] }) {
  return (
    <nav aria-label="Shortcuts">
      <ul className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {links.map(({ href, label, detail, icon: Icon }) => (
          <li key={href}>
            <Link href={href}
              className="group flex h-full items-center gap-3 rounded-2xl border border-border bg-surface p-4 shadow-sm transition duration-300 hover:-translate-y-0.5 hover:border-gold/40 hover:shadow-md">
              <span aria-hidden="true" className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-background text-gold-text transition-colors duration-300 group-hover:bg-gold group-hover:text-on-gold">
                <Icon className="size-[18px]" />
              </span>
              <span className="min-w-0 text-sm leading-tight">
                <span className="block font-semibold">{label}</span>
                <span className="block truncate text-xs text-muted">{detail}</span>
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </nav>
  );
}
