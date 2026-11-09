import { deniedUnlessInMenu } from "@/components/layout/page-access";
import type { Metadata } from "next";
import Link from "next/link";
import { Notice } from "@/components/ui/notice";
import { PageHeader } from "@/components/ui/page-header";
import { DownlineEditor } from "@/features/admin/downline-editor";
import { InvestorPicker } from "@/features/admin/investor-picker";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { AdminDownline } from "@/types/referral";
import type { AdminUserSummary } from "@/types/user";
import { ArrowLeftRight, GitBranch, Hand, RotateCcw, UserMinus, X } from "lucide-react";

export const metadata: Metadata = { title: "Downlines" };

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const HERE = "/platform/downlines";

/**
 * Super administrators arrange investors' downlines: one investor's downline (?investor=), and optionally a second
 * alongside (?with=), to drag members onto a new referrer, into the other downline, or out of their downline.
 */
export default async function DownlinesPage({ searchParams }: PageProps<"/platform/downlines">) {
  const denied = await deniedUnlessInMenu("/platform/downlines");
  if (denied) return denied;
  const query = await searchParams;
  const param = (name: string) => (typeof query[name] === "string" && UUID.test(query[name]) ? query[name] : null);
  const investorId = param("investor");
  const withId = investorId && param("with") !== investorId ? param("with") : null;
  const find = typeof query.find === "string" ? query.find.trim().slice(0, 100) : "";

  const [left, right] = await Promise.all([investorId ? downlineOf(investorId) : null, withId ? downlineOf(withId) : null]);
  // The picker shows for the first investor until one is open, then for the second.
  const picking = !left || !right;
  const found = picking && find
    ? (await authFetch<PageResponse<AdminUserSummary>>(`/api/v1/admin/users?kind=INVESTOR&size=10&q=${encodeURIComponent(find)}`))
      .content.filter((u) => u.id !== left?.investor.userId)
    : null;

  return (
    <div className="space-y-6">
      <PageHeader title="Downlines"
        description="Arrange investors' downlines: drag a member onto their new referrer, into the other downline, or onto “Remove from downline”. Everyone below a member moves with them. Only super administrators can open this page." />
      {!left ? (
        <div className="grid gap-6 lg:grid-cols-[minmax(0,36rem)_minmax(0,1fr)]">
          <InvestorPicker title="Open a downline" description="Find the investor whose downline you want to arrange."
            find={find} found={found} hrefFor={(id) => `${HERE}?investor=${id}`} />
          <ul className="grid gap-3 self-start sm:grid-cols-2">
            {TIPS.map((tip) => (
              <li key={tip.title} className="flex gap-3 rounded-2xl border border-border bg-surface p-4 shadow-sm">
                <span aria-hidden="true" className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-gold/10 text-gold-text">
                  <tip.icon className="size-4" />
                </span>
                <span className="min-w-0 text-sm">
                  <span className="block font-medium">{tip.title}</span>
                  <span className="block text-xs text-muted">{tip.text}</span>
                </span>
              </li>
            ))}
          </ul>
        </div>
      ) : (
        <>
          {[left, right].map((d) => d?.truncated && (
            <Notice key={d.investor.userId} tone="warning">
              {d.investor.name}&apos;s downline is very large: only the first {d.members.length} members are shown.
            </Notice>
          ))}
          <p>
            <Link href={HERE}
              className="inline-flex min-h-9 items-center gap-1.5 rounded-lg border border-border bg-surface px-3 text-sm font-medium shadow-sm transition-colors hover:border-gold hover:text-gold-text">
              <RotateCcw aria-hidden="true" className="size-4" /> Choose a different investor
            </Link>
          </p>
          <DownlineEditor panes={right ? [left, right] : [left]}
            side={right ? (
              <Link href={`${HERE}?investor=${left.investor.userId}`}
                className="inline-flex min-h-9 items-center gap-1.5 rounded-lg border border-border bg-surface px-3 text-sm font-medium shadow-sm transition-colors hover:border-gold hover:text-gold-text">
                <X aria-hidden="true" className="size-4" /> Close {right.investor.name}&apos;s downline
              </Link>
            ) : (
              <InvestorPicker title="Open another downline alongside"
                description="To move members into another investor's downline, find that investor."
                find={find} found={found} keep={{ investor: left.investor.userId }}
                hrefFor={(id) => `${HERE}?investor=${left.investor.userId}&with=${id}`} />
            )} />
        </>
      )}
    </div>
  );
}

const TIPS = [
  { icon: Hand, title: "Drag to move", text: "Drop a member onto their new referrer. Everyone below them moves too." },
  { icon: ArrowLeftRight, title: "Two side by side", text: "Open a second investor to move members between downlines." },
  { icon: UserMinus, title: "Remove", text: "Drop onto “Remove from downline” so nobody refers them." },
  { icon: GitBranch, title: "No mouse?", text: "Use Move on any row to choose the new referrer from a list." },
];

async function downlineOf(userId: string): Promise<AdminDownline | null> {
  try {
    return await authFetch<AdminDownline>(`/api/v1/admin/users/${userId}/downline`);
  } catch (error) {
    if (error instanceof BackendError && error.status === 404) return null;
    throw error;
  }
}
