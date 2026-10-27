import { redirect } from "next/navigation";
import { getOnboarding } from "@/lib/server/onboarding";

/**
 * "Complete onboarding" and similar buttons lead here: on to the first account-setup step still to do (email address,
 * personal details, identity verification, then a bank account), or to the verification status once all are done.
 * A route rather than a page: a page that only redirects is logged by React's development build as an errored
 * component with a negative end time, which Firefox rejects ("Performance.measure: Given attribute end cannot be
 * negative").
 */
export async function GET() {
  const { next } = await getOnboarding();
  redirect(next?.href ?? "/profile/verification");
}
