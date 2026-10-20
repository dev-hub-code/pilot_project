import type { Metadata } from "next";
import { Alert } from "@/components/ui/alert";
import { LinkButton } from "@/components/ui/link-button";
import { verifyEmailToken } from "@/features/auth/actions";
import { ResendVerificationForm } from "@/features/auth/resend-verification-form";
import { VerifyCodeForm } from "@/features/auth/verify-code-form";
import { EmailText } from "@/components/ui/email-text";

export const metadata: Metadata = { title: "Confirm your email" };

/**
 * After sign-up ("check your inbox", with ?email=): the user enters the 6-digit code from the email.
 * Also the target of the email's link (?token=), which confirms the address directly. Either way the
 * user then signs in.
 */
export default async function VerifyEmailPage({ searchParams }: PageProps<"/verify-email">) {
  const params = await searchParams;
  const token = typeof params.token === "string" ? params.token.slice(0, 100) : undefined;
  const email = typeof params.email === "string" ? params.email.slice(0, 254) : undefined;

  if (token) {
    const result = await verifyEmailToken(token);
    if (result.ok) {
      return (
        <div className="space-y-6">
          <div className="space-y-2">
            <h1 className="text-4xl font-semibold tracking-tight">Email confirmed</h1>
            <p className="text-sm text-muted">Your account is active. Sign in to get started.</p>
          </div>
          <LinkButton href="/login?verified=1" className="w-full">Sign in</LinkButton>
        </div>
      );
    }
    return (
      <div className="space-y-6">
        <div className="space-y-2">
          <h1 className="text-4xl font-semibold tracking-tight">Link not valid</h1>
          <p className="text-sm text-muted">Enter your email address and we will send you a new code and link.</p>
        </div>
        <Alert>{result.message}</Alert>
        <ResendVerificationForm />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <h1 className="text-4xl font-semibold tracking-tight">Check your inbox</h1>
        <p className="text-sm text-muted">
          {email ? (
            <>We sent a 6-digit code and a confirmation link to <span className="font-medium text-foreground"><EmailText>{email}</EmailText></span>.</>
          ) : (
            "We sent a 6-digit code and a confirmation link to your email address."
          )}{" "}
          Enter the code below or open the link to activate your account. Both work for 24 hours.
        </p>
      </div>
      <VerifyCodeForm email={email} />
      <div className="space-y-3 border-t border-border pt-6">
        <p className="text-sm text-muted">Nothing arrived, or the code expired? Check your spam folder, or send a new email.</p>
        <ResendVerificationForm email={email} />
      </div>
    </div>
  );
}
