import type { Metadata } from "next";
import { ContainerScene } from "@/components/brand/container-scene";
import { Eyebrow } from "@/components/brand/eyebrow";
import { Card } from "@/components/ui/card";
import { LinkButton } from "@/components/ui/link-button";
import { StatusBadge } from "@/components/ui/status-badge";
import { toneFor } from "@/components/ui/status-tones";
import { authFetch } from "@/lib/server/auth/session";
import type { CurrentUser } from "@/types/auth";
import type { Profile } from "@/types/user";
import { humanize } from "@/utils/format";

export const metadata: Metadata = { title: "Dashboard" };

const KPIS = ["Total invested", "Rental income", "Available balance"] as const;

export default async function DashboardPage() {
  const [me, profile] = await Promise.all([
    authFetch<CurrentUser>("/api/v1/auth/me"),
    authFetch<Profile>("/api/v1/users/me"),
  ]);
  const needsVerification = profile.kycStatus !== "APPROVED";

  return (
    <div className="space-y-8">
      <section className="relative isolate overflow-hidden bg-ink px-6 py-12 text-on-ink sm:px-10 sm:py-16">
        <ContainerScene focus="side" className="absolute inset-0 -z-10 size-full" />
        <div aria-hidden="true" className="absolute inset-0 -z-10 bg-gradient-to-r from-ink via-ink/85 to-ink/10" />
        <div className="max-w-xl space-y-5">
          <Eyebrow tone="light">Investor Dashboard</Eyebrow>
          <h1 className="text-4xl leading-tight font-bold tracking-tight sm:text-5xl">Welcome back, {me.firstName}</h1>
          {needsVerification ? (
            <div className="space-y-5">
              <p className="text-on-ink/85">
                Verify your identity to start investing and to receive withdrawals.
              </p>
              <LinkButton href="/profile/verification">
                {profile.kycStatus === "PENDING" ? "View Verification Status" : "Verify My Identity"}
              </LinkButton>
            </div>
          ) : (
            <div className="space-y-5">
              <p className="text-on-ink/85">Your account is verified. Explore containers open for investment.</p>
              <LinkButton href="/marketplace">Browse The Marketplace</LinkButton>
            </div>
          )}
        </div>
      </section>

      <section className="grid gap-px bg-border sm:grid-cols-3" aria-label="Portfolio summary">
        {KPIS.map((label) => (
          <div key={label} className="space-y-3 bg-surface p-6">
            <p className="text-xs uppercase tracking-[0.1em] text-muted">{label}</p>
            <p className="font-display text-4xl font-semibold tabular-nums">—</p>
            <p className="text-xs text-muted">Available once investing opens</p>
          </div>
        ))}
      </section>

      <Card title="Account">
        <dl className="grid gap-6 text-sm sm:grid-cols-4">
          <Item label="Account status">
            <StatusBadge tone={toneFor(me.status)}>{humanize(me.status)}</StatusBadge>
          </Item>
          <Item label="Identity verification">
            <StatusBadge tone={toneFor(profile.kycStatus)}>{humanize(profile.kycStatus)}</StatusBadge>
          </Item>
          <Item label="Investor type">
            <StatusBadge tone={toneFor(profile.investorType)}>{profile.investorType}</StatusBadge>
          </Item>
          <Item label="Roles">{me.roles.join(", ") || "—"}</Item>
        </dl>
      </Card>
    </div>
  );
}

function Item({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="space-y-2">
      <dt className="text-xs uppercase tracking-[0.1em] text-muted">{label}</dt>
      <dd className="font-medium">{children}</dd>
    </div>
  );
}
