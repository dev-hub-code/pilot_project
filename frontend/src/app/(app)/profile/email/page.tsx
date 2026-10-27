import type { Metadata } from "next";
import { Card } from "@/components/ui/card";
import { EmailText } from "@/components/ui/email-text";
import { LinkButton } from "@/components/ui/link-button";
import { Notice } from "@/components/ui/notice";
import { ResendVerificationForm } from "@/features/auth/resend-verification-form";
import { VerifyCodeForm } from "@/features/auth/verify-code-form";
import { authFetch } from "@/lib/server/auth/session";
import { onboardingHref } from "@/lib/server/onboarding";
import type { CurrentUser } from "@/types/auth";

export const metadata: Metadata = { title: "Email address" };

/**
 * The first account-setup step for those who signed in without confirming their email address: the 6-digit code from
 * the email, or a new email. The other steps wait for it.
 */
export default async function EmailAddressPage() {
  const me = await authFetch<CurrentUser>("/api/v1/auth/me");

  if (me.emailVerified) {
    return (
      <Card title="Email address">
        <div className="space-y-4">
          <Notice tone="success"><EmailText>{me.email}</EmailText> is confirmed.</Notice>
          <LinkButton href={await onboardingHref()}>Continue account setup</LinkButton>
        </div>
      </Card>
    );
  }

  return (
    <div className="grid gap-6 lg:grid-cols-3">
      <Card title="Confirm your email address" className="lg:col-span-2">
        <div className="space-y-6">
          <p className="text-sm text-muted">
            We sent a 6-digit code to <span className="font-medium text-foreground"><EmailText>{me.email}</EmailText></span>.
            Enter it below, or open the link in that email. Identity verification and bank accounts open once your email
            address is confirmed.
          </p>
          <VerifyCodeForm email={me.email} onboarding />
          <div className="space-y-3 border-t border-border pt-6">
            <p className="text-sm text-muted">Nothing arrived, or the code expired? Check your spam folder, or send a new email.</p>
            <ResendVerificationForm email={me.email} />
          </div>
        </div>
      </Card>
    </div>
  );
}
