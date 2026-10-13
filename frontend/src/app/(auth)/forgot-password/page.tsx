import type { Metadata } from "next";
import Link from "next/link";
import { ForgotPasswordForm } from "@/features/auth/forgot-password-form";

export const metadata: Metadata = { title: "Forgot password" };

export default async function ForgotPasswordPage({ searchParams }: PageProps<"/forgot-password">) {
  const params = await searchParams;
  const email = typeof params.email === "string" ? params.email.slice(0, 254) : undefined;
  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <h1 className="text-4xl font-semibold tracking-tight">Forgot password</h1>
        <p className="text-sm text-muted">Enter your account&apos;s email address and we will email you a code to set a new password.</p>
      </div>
      <ForgotPasswordForm email={email} />
      <p className="text-center text-sm text-muted">
        Remembered it?{" "}
        <Link href="/login" className="font-medium text-foreground underline decoration-gold decoration-2 underline-offset-4 hover:text-gold-text">
          Sign in
        </Link>
      </p>
    </div>
  );
}
