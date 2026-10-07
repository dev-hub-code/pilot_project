import type { Metadata } from "next";
import { RegisterForm } from "@/features/auth/register-form";

export const metadata: Metadata = { title: "Create account" };

export default function RegisterPage() {
  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <h1 className="text-4xl font-semibold tracking-tight">Create your account</h1>
        <p className="text-sm text-muted">Start investing in container rental assets.</p>
      </div>
      <RegisterForm />
    </div>
  );
}
