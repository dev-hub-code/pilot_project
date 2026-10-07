import type { Metadata } from "next";
import { LoginForm } from "@/features/auth/login-form";
import { safeRedirectPath } from "@/utils/redirect";

export const metadata: Metadata = { title: "Sign in" };

export default async function LoginPage({ searchParams }: PageProps<"/login">) {
  const params = await searchParams;
  const next = typeof params.next === "string" ? safeRedirectPath(params.next) : undefined;
  const expired = params.expired === "1";

  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <h1 className="text-4xl font-semibold tracking-tight">Sign in</h1>
        <p className="text-sm text-muted">
          {expired ? "Your session ended. Please sign in again." : "Welcome back to your portfolio."}
        </p>
      </div>
      <LoginForm next={next} />
    </div>
  );
}
