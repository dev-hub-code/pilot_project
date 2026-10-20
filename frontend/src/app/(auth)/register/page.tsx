import type { Metadata } from "next";
import { RegisterForm } from "@/features/auth/register-form";
import { getPhoneCountries } from "@/lib/server/phone-countries";

export const metadata: Metadata = { title: "Create account" };

export default async function RegisterPage({ searchParams }: PageProps<"/register">) {
  const ref = (await searchParams).ref;
  const referralCode = typeof ref === "string" ? ref.slice(0, 8).toUpperCase() : undefined;
  const countries = await getPhoneCountries();
  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <h1 className="text-4xl font-semibold tracking-tight">Create your account</h1>
        <p className="text-sm text-muted">Start investing in container rental assets.</p>
      </div>
      <RegisterForm referralCode={referralCode} countries={countries} />
    </div>
  );
}
