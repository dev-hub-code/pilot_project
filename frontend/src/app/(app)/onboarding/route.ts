import { redirect } from "next/navigation";
import { getOnboarding } from "@/lib/server/onboarding";

/**
 * "Complete onboarding" and similar buttons lead here: on to the first account-setup step still to do (personal
 * details, then identity verification, then a bank account), or to the verification status once all are done.
 */
export default async function OnboardingPage() {
  const { next } = await getOnboarding();
  redirect(next?.href ?? "/profile/verification");
}
