import "server-only";
import { redirect } from "next/navigation";
import { missingPersonalDetails } from "@/features/profile/kyc-labels";
import { authFetch } from "@/lib/server/auth/session";
import type { CurrentUser } from "@/types/auth";
import type { BankAccount, Profile } from "@/types/user";

export interface OnboardingStep {
  key: "email" | "personal" | "identity" | "bank";
  label: string;
  href: string;
  done: boolean;
}

export interface Onboarding {
  steps: OnboardingStep[];
  /** The first step still to do, in order; null once all are done. */
  next: OnboardingStep | null;
}

/**
 * An investor's account setup, in the order it must be done: confirming the email address (only for those who have
 * not yet; the backend takes no identity documents or bank accounts before it), personal details (identity
 * verification takes the name, date of birth and nationality from them), then identity verification (submitted is enough to move on), then a
 * bank account for payouts (added is enough; finance verifies it once the identity is approved).
 */
/**
 * Where "Complete onboarding" and similar buttons go: the first setup step still to do, or the verification status once
 * all are done. Linking straight there, rather than to the /onboarding route that redirects, spares a client-side
 * navigation a redirect (which also trips Next's development tools when the app is opened by network address).
 */
export async function onboardingHref(): Promise<string> {
  return (await getOnboarding()).next?.href ?? "/profile/verification";
}

export async function getOnboarding(): Promise<Onboarding> {
  const [me, profile, accounts] = await Promise.all([
    authFetch<CurrentUser>("/api/v1/auth/me"),
    authFetch<Profile>("/api/v1/users/me"),
    authFetch<BankAccount[]>("/api/v1/users/me/bank-accounts"),
  ]);
  return onboardingOf(me.emailVerified, profile, accounts);
}

export function onboardingOf(emailVerified: boolean, profile: Profile, accounts: BankAccount[]): Onboarding {
  const steps: OnboardingStep[] = [
    // Shown only while it is still to do: accounts confirmed at sign-up never see it.
    ...(emailVerified ? [] : [{ key: "email", label: "Email address", href: "/profile/email", done: false } as const]),
    { key: "personal", label: "Personal details", href: "/profile", done: missingPersonalDetails(profile).length === 0 },
    { key: "identity", label: "Identity verification", href: "/profile/verification",
      done: profile.kycStatus === "PENDING" || profile.kycStatus === "APPROVED" },
    { key: "bank", label: "Bank account", href: "/profile/bank-accounts",
      done: accounts.some((a) => a.status === "PENDING_VERIFICATION" || a.status === "VERIFIED") },
  ];
  // Steps are done in order: a later step counts only once those before it are done.
  const next = steps.find((step) => !step.done) ?? null;
  return { steps, next };
}

/** The account-setup pages after the first wait for the email address: until it is confirmed they lead to it. */
export async function requireEmailVerified(): Promise<void> {
  const me = await authFetch<CurrentUser>("/api/v1/auth/me");
  if (!me.emailVerified) redirect("/profile/email");
}
