import type { Metadata } from "next";
import { notFound, redirect } from "next/navigation";
import { PageHeader } from "@/components/ui/page-header";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch } from "@/lib/server/auth/session";
import { creditLabel, getRentCredit } from "@/lib/server/rent-credit";
import type { DownlineMember, ReferralOverview } from "@/types/referral";
import { formatDate } from "@/utils/format";
import { formatMoney } from "@/utils/money";

export const metadata: Metadata = { title: "Referral member" };

/** One member of the investor's downline, and the commission earned from them. */
export default async function ReferralMemberPage({ params }: PageProps<"/referrals/members/[id]">) {
  const { id } = await params;
  if (!/^m[1-9]\d{0,8}$/.test(id)) notFound();
  // Online investors have no member pages: their page lists the people they invited, with what each earned them.
  const overview = await authFetch<ReferralOverview>("/api/v1/referrals/me");
  if (overview.scheme === "ONLINE") redirect("/referrals");
  let member: DownlineMember;
  try {
    member = await authFetch<DownlineMember>(`/api/v1/referrals/members/${id}`);
  } catch (error) {
    if (error instanceof BackendError && error.status === 404) notFound();
    throw error;
  }
  const credit = creditLabel(await getRentCredit());
  const earned = member.earned.length === 0 ? "—" : member.earned.map((m) => formatMoney(m)).join(" · ");

  return (
    <div className="space-y-6">
      <PageHeader title={member.displayName} description={`Level ${member.level} · joined ${formatDate(member.joinedAt)}`} />

      <section className="grid gap-px overflow-hidden rounded-xl border border-border bg-border" aria-label="Commission from this member">
        <div className="space-y-2 bg-surface p-6">
          <p className="text-sm text-muted">You earned from {member.displayName}</p>
          <p className="font-display text-3xl font-semibold tabular-nums">{earned}</p>
          <p className="text-xs text-muted">Commission is credited {credit}, alongside this member&apos;s returns.</p>
        </div>
      </section>
    </div>
  );
}
