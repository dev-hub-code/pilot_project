import Link from "next/link";
import { Button } from "@/components/ui/button";
import { EmailText } from "@/components/ui/email-text";
import { TextField } from "@/components/ui/text-field";
import type { AdminUserSummary } from "@/types/user";

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
    <div className="space-y-4 border border-dashed border-border bg-surface p-4">
      <div className="space-y-1">
        <h2 className="font-semibold">{title}</h2>
        <p className="text-sm text-muted">{description}</p>
      </div>
      <form className="flex items-end gap-2" role="search">
        {Object.entries(keep ?? {}).map(([name, value]) => <input key={name} type="hidden" name={name} value={value} />)}
        <div className="flex-1">
          <TextField label="Investor" name="find" type="search" placeholder="Email, name or ID (SL03500)" defaultValue={find} />
        </div>
        <Button type="submit" variant="quiet">Find</Button>
      </form>
      {found && (found.length === 0 ? (
        <p className="text-sm text-muted">No investor matches “{find}”.</p>
      ) : (
        <ul className="divide-y divide-border border border-border">
          {found.map((u) => (
            <li key={u.id}>
              <Link href={hrefFor(u.id)} className="block px-3 py-2 text-sm hover:bg-foreground/5">
                <span className="font-medium">{u.firstName} {u.lastName}</span>{" "}
                <span className="font-mono text-xs text-muted">{u.userCode}</span>
                <span className="block text-xs text-muted"><EmailText>{u.email}</EmailText></span>
              </Link>
            </li>
          ))}
        </ul>
      ))}
    </div>
  );
}
