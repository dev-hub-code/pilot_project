"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { OnboardingStep } from "@/lib/server/onboarding";

/**
 * Account setup as numbered steps, shown in place of the profile tabs until all are done. Done steps and the one to
 * do next can be opened; later steps wait until those before them are done.
 */
export function OnboardingSteps({ steps }: { steps: OnboardingStep[] }) {
  const pathname = usePathname();
  const nextIndex = steps.findIndex((step) => !step.done);
  const doneCount = steps.filter((step) => step.done).length;

  return (
    <nav aria-label="Account setup" className="space-y-3 border-b border-border pb-4">
      <p className="text-sm text-muted">
        Set up your account to invest: {doneCount} of {steps.length} steps done.
      </p>
      <ol className="grid gap-2 sm:grid-cols-3">
        {steps.map((step, index) => {
          const current = pathname === step.href;
          const reachable = step.done || index === nextIndex;
          const body = (
            <>
              <span aria-hidden="true"
                className={`flex size-7 shrink-0 items-center justify-center rounded-full text-xs font-semibold ${
                  step.done ? "bg-gold text-on-gold" : index === nextIndex ? "border-2 border-gold text-gold-text" : "border border-border text-muted"
                }`}>
                {step.done ? "✓" : index + 1}
              </span>
              <span className="min-w-0">
                <span className={`block text-sm ${current ? "font-semibold" : "font-medium"}`}>{step.label}</span>
                <span className="block text-xs text-muted">
                  {step.done ? "Done" : index === nextIndex ? "Next step" : `After step ${index}`}
                </span>
              </span>
            </>
          );
          const box = `flex items-center gap-3 border p-3 ${current ? "border-gold bg-gold/10" : "border-border bg-surface"}`;
          return (
            <li key={step.key}>
              {reachable ? (
                <Link href={step.href} aria-current={current ? "step" : undefined} className={`${box} hover:border-gold`}>
                  {body}
                  <span className="sr-only">{step.done ? "(done)" : "(next step)"}</span>
                </Link>
              ) : (
                <div className={`${box} opacity-60`} aria-disabled="true">
                  {body}
                </div>
              )}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
