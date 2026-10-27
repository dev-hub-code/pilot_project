import Link from "next/link";
import { EmailText } from "@/components/ui/email-text";
import { StatusBadge } from "@/components/ui/status-badge";
import { toneFor } from "@/components/ui/status-tones";
import type { AdminDownline, AdminDownlineMember } from "@/types/referral";
import { formatDate, humanize } from "@/utils/format";

/** Levels 1-4 earn commission. */
const COMMISSION_LEVELS = 4;

type Node = AdminDownlineMember & { children: Node[] };

/** An investor's downline for staff, read only: everyone below them, every level, each linking to their user page. */
export function DownlineView({ downline }: { downline: AdminDownline }) {
  const nodes = new Map<string, Node>(downline.members.map((m) => [m.userId, { ...m, children: [] }]));
  const top: Node[] = [];
  for (const node of nodes.values()) {
    // Members whose referrer is not listed (a truncated list) show at the top.
    (nodes.get(node.referrerId)?.children ?? top).push(node);
  }
  if (top.length === 0) return <p className="text-sm text-muted">Nobody is in this investor&apos;s downline.</p>;
  return (
    <div className="space-y-2">
      {downline.truncated && (
        <p className="text-xs text-muted">Very large downline: only the first {downline.members.length} members are shown.</p>
      )}
      <div className="max-h-[32rem] overflow-y-auto pr-1">
        <Branch nodes={top} />
      </div>
    </div>
  );
}

function Branch({ nodes, nested = false }: { nodes: Node[]; nested?: boolean }) {
  return (
    <ul className={nested ? "ml-4 space-y-2 border-l border-border pl-4" : "space-y-2"}>
      {nodes.map((node) => (
        <li key={node.userId} className="space-y-2">
          <div className="border border-border bg-surface px-4 py-2.5 text-sm">
            <span className="flex flex-wrap items-center gap-2">
              <span className="rounded-full border border-border px-2 py-0.5 text-[11px] tracking-[0.08em] text-muted uppercase">
                Level {node.level}{node.level > COMMISSION_LEVELS ? " · no commission" : ""}
              </span>
              <Link href={`/admin/users/${node.userId}`} className="font-medium text-gold-text hover:underline">{node.name}</Link>
              {node.status !== "ACTIVE" && <StatusBadge tone={toneFor(node.status)}>{humanize(node.status)}</StatusBadge>}
            </span>
            <span className="block text-xs text-muted wrap-anywhere">
              <EmailText>{node.email}</EmailText> · joined {formatDate(node.joinedAt)}
            </span>
          </div>
          {node.children.length > 0 && <Branch nodes={node.children} nested />}
        </li>
      ))}
    </ul>
  );
}
