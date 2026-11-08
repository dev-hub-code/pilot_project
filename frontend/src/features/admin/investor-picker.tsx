import Link from "next/link";
import { EmailText } from "@/components/ui/email-text";
import type { AdminUserSummary } from "@/types/user";
import { ChevronRight, Search, UserSearch } from "lucide-react";

/**
 * Finds an investor by a search in the page's URL (?find=), keeping the URL's other parameters, and links each match
 * to {@code hrefFor(id)}.
 *
 * @param found  the matches, or null before a search
 * @param keep   query parameters the search form carries along (e.g. the downline already open)
 */
export function InvestorPicker({ title, description, find, found, keep, hrefFor }: {
  title: string;
  description: string;
  find: string;
  found: AdminUserSummary[] | null;
  keep?: Record<string, string>;
  hrefFor: (userId: string) => string;
}) {
  return (
    <div className="space-y-4 rounded-2xl border border-border bg-surface p-5 shadow-sm">
      <div className="flex items-start gap-3">
        <span aria-hidden="true" className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-gold/10 text-gold-text">
          <UserSearch className="size-5" />
        </span>
        <div className="min-w-0 space-y-0.5">
          <h2 className="font-semibold">{title}</h2>
          <p className="text-sm text-muted">{description}</p>
        </div>
      </div>
      <form role="search" className="flex items-center gap-2 rounded-xl border border-border bg-background/60 p-1 focus-within:border-gold">
        {Object.entries(keep ?? {}).map(([name, value]) => <input key={name} type="hidden" name={name} value={value} />)}
        <Search aria-hidden="true" className="ml-2.5 size-4 shrink-0 text-muted" />
        <input name="find" type="search" defaultValue={find} placeholder="Email, name or ID (SL03500)" aria-label="Find an investor"
          className="h-9 min-w-0 flex-1 bg-transparent px-1 text-sm outline-none" />
        <button type="submit" className="h-9 rounded-lg bg-gold px-4 text-sm font-semibold text-on-gold transition-colors hover:bg-gold-hover">Find</button>
      </form>
      {found && (found.length === 0 ? (
        <p className="text-sm text-muted">No investor matches “{find}”.</p>
      ) : (
        <ul className="divide-y divide-border overflow-hidden rounded-xl border border-border">
          {found.map((u) => (
            <li key={u.id}>
              <Link href={hrefFor(u.id)} className="group flex items-center gap-3 px-3 py-2.5 text-sm transition-colors hover:bg-background">
                <span aria-hidden="true" className="flex size-8 shrink-0 items-center justify-center rounded-full bg-gold/10 text-[11px] font-semibold text-gold-text transition-colors group-hover:bg-gold group-hover:text-on-gold">
                  {(u.firstName.charAt(0) + u.lastName.charAt(0)).toUpperCase() || "?"}
                </span>
                <span className="min-w-0 flex-1">
                  <span className="font-medium group-hover:text-gold-text">{u.firstName} {u.lastName}</span>{" "}
                  <span className="font-mono text-xs text-muted">{u.userCode}</span>
                  <span className="block truncate text-xs text-muted"><EmailText>{u.email}</EmailText></span>
                </span>
                <ChevronRight aria-hidden="true" className="size-4 shrink-0 text-muted transition-transform group-hover:translate-x-0.5" />
              </Link>
            </li>
          ))}
        </ul>
      ))}
    </div>
  );
}
