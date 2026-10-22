import "server-only";
import { missingPersonalDetails } from "@/features/profile/kyc-labels";
import { authFetch } from "@/lib/server/auth/session";
import type { BankAccount, Profile } from "@/types/user";

export interface OnboardingStep {
  key: "personal" | "identity" | "bank";
  label: string;
  href: string;
  done: boolean;
}

export interface Onboarding {
  steps: OnboardingStep[];
  /** The first step still to do, in order; null once all three are done. */
  next: OnboardingStep | null;
}

/**
 * An investor's account setup, in the order it must be done: personal details (identity verification takes the name,
 * date of birth and nationality from them), then identity verification (submitted is enough to move on), then a
 * bank account for payouts (added is enough; finance verifies it once the identity is approved).
 */
export async function getOnboarding(): Promise<Onboarding> {
  const [profile, accounts] = await Promise.all([
    authFetch<Profile>("/api/v1/users/me"),
    authFetch<BankAccount[]>("/api/v1/users/me/bank-accounts"),
  ]);
  return onboardingOf(profile, accounts);
}

export function onboardingOf(profile: Profile, accounts: BankAccount[]): Onboarding {
  const steps: OnboardingStep[] = [
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
