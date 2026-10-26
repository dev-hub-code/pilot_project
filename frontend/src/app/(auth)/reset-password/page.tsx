import type { Metadata } from "next";
import Link from "next/link";
import { ForgotPasswordForm } from "@/features/auth/forgot-password-form";
import { ResetPasswordForm } from "@/features/auth/reset-password-form";
import { EmailText } from "@/components/ui/email-text";

export const metadata: Metadata = { title: "Set a new password" };

/** Forgot password, step 2: the emailed code and the new password (?email= from step 1 or the email's link). */
export default async function ResetPasswordPage({ searchParams }: PageProps<"/reset-password">) {
  const params = await searchParams;
  const email = typeof params.email === "string" ? params.email.slice(0, 254) : undefined;
  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <h1 className="text-4xl font-semibold tracking-tight">Set a new password</h1>
        <p className="text-sm text-muted">
          {email ? (
            <>We emailed a 6-digit code to <span className="font-medium text-foreground"><EmailText>{email}</EmailText></span>.</>
          ) : (
            "Enter your email address and the 6-digit code we emailed you."
          )}{" "}
          The code works for 15 minutes.
        </p>
      </div>
      <ResetPasswordForm email={email} />
      <div className="space-y-3 border-t border-border pt-6">
        <p className="text-sm text-muted">Nothing arrived? Check your spam folder, or send a new code.</p>
        {email ? (
          <ForgotPasswordForm email={email} stay />
        ) : (
          <Link href="/forgot-password" className="text-sm font-medium underline underline-offset-4">Request a code</Link>
        )}
      </div>
    </div>
  );
}
