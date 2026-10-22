import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { PageHeader } from "@/components/ui/page-header";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch } from "@/lib/server/auth/session";
import type { DownlineMember } from "@/types/referral";
import { formatDate } from "@/utils/format";
import { formatMoney } from "@/utils/money";

export const metadata: Metadata = { title: "Referral member" };

/** One member of the investor's downline, and the commission earned from them. */
export default async function ReferralMemberPage({ params }: PageProps<"/referrals/members/[id]">) {
  const { id } = await params;
  if (!/^m[1-9]\d{0,8}$/.test(id)) notFound();
  let member: DownlineMember;
  try {
    member = await authFetch<DownlineMember>(`/api/v1/referrals/members/${id}`);
  } catch (error) {
    if (error instanceof BackendError && error.status === 404) notFound();
    throw error;
  }
  const earned = member.earned.length === 0 ? "—" : member.earned.map((m) => formatMoney(m)).join(" · ");

  return (
    <div className="space-y-6">
      <PageHeader title={member.displayName} description={`Level ${member.level} · joined ${formatDate(member.joinedAt)}`} />

      <section className="grid gap-px bg-border" aria-label="Commission from this member">
        <div className="space-y-2 bg-surface p-6">
          <p className="text-xs uppercase tracking-[0.1em] text-muted">You earned from {member.displayName}</p>
          <p className="font-display text-3xl font-semibold tabular-nums">{earned}</p>
          <p className="text-xs text-muted">Commission is credited every hour, alongside the rent on this member&apos;s containers.</p>
        </div>
      </section>
    </div>
  );
}
