import Link from "next/link";
import type { DownlineMember } from "@/types/referral";
import { formatDate } from "@/utils/format";
import { formatMoney } from "@/utils/money";

type Node = DownlineMember & { children: Node[] };

/** The downline as nested lists, built from the flat response (each member names its referrer). */
export function DownlineTree({ members }: { members: DownlineMember[] }) {
  const nodes = new Map<string, Node>(members.map((m) => [m.id, { ...m, children: [] }]));
  const roots: Node[] = [];
  for (const node of nodes.values()) {
    const parent = node.parentId ? nodes.get(node.parentId) : undefined;
    if (parent) parent.children.push(node);
    else roots.push(node);
  }
  return <Branch nodes={roots} />;
}

function Branch({ nodes, nested = false }: { nodes: Node[]; nested?: boolean }) {
  return (
    <ul className={nested ? "ml-4 space-y-2 border-l border-border pl-4" : "space-y-2"}>
      {nodes.map((node) => (
        <li key={node.id} className="space-y-2">
          <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 border border-border bg-surface px-4 py-3 text-sm">
            <span>
              <span className="mr-2 rounded-full border border-border px-2 py-0.5 text-[11px] tracking-[0.08em] text-muted uppercase">
                Level {node.level}
              </span>
              <Link href={`/referrals/members/${node.id}`} className="font-medium hover:underline">{node.displayName}</Link>
              <span className="ml-2 text-xs text-muted">joined {formatDate(node.joinedAt)}</span>
            </span>
            <span className="tabular-nums text-muted">
              {node.earned.length > 0 ? `You earned ${node.earned.map((m) => formatMoney(m)).join(" · ")}` : "No commission yet"}
            </span>
          </div>
          {node.children.length > 0 && <Branch nodes={node.children} nested />}
        </li>
      ))}
    </ul>
  );
}
