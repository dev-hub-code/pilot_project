import { BadgeCheck, Landmark, Mail, ShieldCheck, UserRound } from "lucide-react";
import { PortPhoto } from "@/components/brand/port-photo";
import { SubNav } from "@/components/layout/sub-nav";
import { StatusBadge } from "@/components/ui/status-badge";
import { toneFor } from "@/components/ui/status-tones";
import { OnboardingSteps } from "@/features/profile/onboarding-steps";
import { isInvestor } from "@/lib/permissions";
import { authFetch, requireSession } from "@/lib/server/auth/session";
import { getOnboarding } from "@/lib/server/onboarding";
import type { Profile } from "@/types/user";
import { humanize } from "@/utils/format";

const INVESTOR_TABS = [
  { href: "/profile", label: "Personal details", icon: <UserRound /> },
  { href: "/profile/verification", label: "Identity verification", icon: <ShieldCheck /> },
  { href: "/profile/bank-accounts", label: "Bank accounts", icon: <Landmark /> },
];

export default async function ProfileLayout({ children }: LayoutProps<"/profile">) {
  const investor = isInvestor((await requireSession()).permissions);
  // Until an investor has set up their account, the tabs become its steps, in the order they must be done.
  const [onboarding, profile] = await Promise.all([
    investor ? getOnboarding() : Promise.resolve(null),
    // The banner is a convenience: a failure here must not break the page below it.
    authFetch<Profile>("/api/v1/users/me").catch(() => null),
  ]);
  const name = profile ? `${profile.firstName} ${profile.lastName}`.trim() || profile.email : "Your profile";
  const initials = profile
    ? (profile.firstName.charAt(0) + profile.lastName.charAt(0)).toUpperCase() || profile.email.charAt(0).toUpperCase()
    : "?";

  return (
    <div className="space-y-6">
      {/* ------------------------------------------------------------------ banner */}
      <section aria-labelledby="profile-title" className="relative isolate overflow-hidden rounded-3xl bg-ink text-on-ink shadow-xl shadow-ink/20">
        <PortPhoto name="container-wall" alt="" sizes="(min-width: 1280px) 1216px, 100vw"
          className="absolute inset-0 -z-20 size-full object-cover saturate-[0.8]" />
        <div aria-hidden="true" className="absolute inset-0 -z-10 bg-[linear-gradient(100deg,var(--ink)_0%,rgb(11_42_85/0.92)_55%,rgb(11_42_85/0.6)_100%)]" />
        <div aria-hidden="true" className="absolute -top-24 -right-24 -z-10 size-72 rounded-full bg-gold/30 blur-3xl" />
        <div className="flex flex-wrap items-center gap-5 p-6 sm:gap-6 sm:p-8 lg:p-10">
          <span aria-hidden="true"
            className="flex size-16 shrink-0 items-center justify-center rounded-2xl bg-[linear-gradient(140deg,var(--gold),var(--gold-on-ink))] font-display text-2xl font-semibold text-on-gold shadow-lg ring-4 ring-on-ink/15 sm:size-20 sm:text-3xl">
            {initials}
          </span>
          <div className="min-w-0 flex-1 space-y-1.5">
            <p className="text-xs font-medium tracking-[0.14em] text-on-ink/60 uppercase">Profile</p>
            <h1 id="profile-title" className="truncate text-2xl font-semibold tracking-tight sm:text-3xl">{name}</h1>
            {profile && (
              <p className="flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-on-ink/70">
                <span className="inline-flex min-w-0 items-center gap-1.5"><Mail aria-hidden="true" className="size-4 shrink-0" /><span className="truncate">{profile.email}</span></span>
                <span className="inline-flex items-center gap-1.5 font-mono text-xs"><BadgeCheck aria-hidden="true" className="size-4" />{profile.userCode}</span>
              </p>
            )}
          </div>
          {profile && investor && (
            <dl className="grid w-full grid-cols-2 gap-2 sm:w-auto sm:gap-3">
              <div className="rounded-2xl border border-on-ink/15 bg-on-ink/[0.08] px-4 py-3 backdrop-blur-md">
                <dt className="text-[11px] text-on-ink/60">Account</dt>
                <dd className="mt-1"><StatusBadge tone={toneFor(profile.status)}>{humanize(profile.status)}</StatusBadge></dd>
              </div>
              <div className="rounded-2xl border border-on-ink/15 bg-on-ink/[0.08] px-4 py-3 backdrop-blur-md">
                <dt className="text-[11px] text-on-ink/60">Identity</dt>
                <dd className="mt-1"><StatusBadge tone={toneFor(profile.kycStatus)}>{humanize(profile.kycStatus)}</StatusBadge></dd>
              </div>
            </dl>
          )}
        </div>
      </section>

      {/* Staff are not investors: no identity verification or payout accounts, so no tabs. */}
      {onboarding?.next ? <OnboardingSteps steps={onboarding.steps} />
        : investor && <SubNav items={INVESTOR_TABS} label="Profile sections" />}
      {children}
    </div>
  );
}
