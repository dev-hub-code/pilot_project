import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Notice } from "@/components/ui/notice";
import { PageHeader } from "@/components/ui/page-header";
import { DownlineEditor } from "@/features/admin/downline-editor";
import { InvestorPicker } from "@/features/admin/investor-picker";
import { Permission, hasPermission } from "@/lib/permissions";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { AdminDownline } from "@/types/referral";
import type { AdminUserSummary } from "@/types/user";

export const metadata: Metadata = { title: "Downlines" };

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const HERE = "/admin/downlines";

/**
 * Super administrators arrange investors' downlines: one investor's downline (?investor=), and optionally a second
 * alongside (?with=), to drag members onto a new referrer, into the other downline, or out of their downline.
 */
export default async function DownlinesPage({ searchParams }: PageProps<"/admin/downlines">) {
  const session = await requireStaff();
  if (!hasPermission(session.permissions, Permission.REFERRAL_TREE_MANAGE)) notFound();
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
        <div className="max-w-xl">
          <InvestorPicker title="Open a downline" description="Find the investor whose downline you want to arrange."
            find={find} found={found} hrefFor={(id) => `${HERE}?investor=${id}`} />
        </div>
      ) : (
        <>
          {[left, right].map((d) => d?.truncated && (
            <Notice key={d.investor.userId} tone="warning">
              {d.investor.name}&apos;s downline is very large: only the first {d.members.length} members are shown.
            </Notice>
          ))}
          <p className="text-sm">
            <Link href={HERE} className="text-gold-text hover:underline">Choose a different investor</Link>
          </p>
          <DownlineEditor panes={right ? [left, right] : [left]}
            side={right ? (
              <Link href={`${HERE}?investor=${left.investor.userId}`} className="text-sm text-gold-text hover:underline">
                Close {right.investor.name}&apos;s downline
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

async function downlineOf(userId: string): Promise<AdminDownline | null> {
  try {
    return await authFetch<AdminDownline>(`/api/v1/admin/users/${userId}/downline`);
  } catch (error) {
    if (error instanceof BackendError && error.status === 404) return null;
    throw error;
  }
}
