import { ShieldCheck } from "lucide-react";
import type { Metadata } from "next";
import Link from "next/link";
import { LoginForm } from "@/features/auth/login-form";
import { safeRedirectPath } from "@/utils/redirect";

export const metadata: Metadata = { title: "Staff sign in", robots: { index: false, follow: false } };

/** Sign-in for SeaLease staff and administrators. Investors use /login; the console is at /platform. */
export default async function PlatformLoginPage({ searchParams }: PageProps<"/platform/login">) {
  const params = await searchParams;
  const next = typeof params.next === "string" ? safeRedirectPath(params.next, "/platform") : undefined;
  const expired = params.expired === "1";

  return (
    <div className="space-y-6">
      <div className="space-y-3">
        <span aria-hidden="true" className="flex size-12 items-center justify-center rounded-2xl bg-gold/10 text-gold-text">
          <ShieldCheck className="size-6" />
        </span>
        <h1 className="text-3xl font-semibold tracking-tight">Staff sign in</h1>
        <p className="text-sm text-muted">
          {expired
            ? "You were signed out, for example because your account signed in on another device. Please sign in again."
            : "For SeaLease employees and administrators."}
        </p>
      </div>
      <LoginForm next={next} portal="staff" emailLabel="Work email" footer={
        <p className="text-center text-xs text-muted">
          Accounts are created by an administrator. Investors sign in{" "}
          <Link href="/login" className="underline underline-offset-4 hover:text-foreground">here</Link>.
        </p>
      } />
    </div>
  );
}
