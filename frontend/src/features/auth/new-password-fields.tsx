"use client";

import { useState } from "react";
import { TextField } from "@/components/ui/text-field";
import { PASSWORD_CHECKS, PASSWORD_MAX, PASSWORD_MIN } from "@/validators/auth";

/**
 * Password and confirmation for opening an account, with a live checklist below that ticks off each rule as it is
 * met, and whether the two match. The server still checks: this only shows progress while typing.
 */
export function NewPasswordFields({ errors }: { errors: { password?: string; confirmPassword?: string } }) {
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const checks = [
    ...PASSWORD_CHECKS.map((check) => ({ label: check.label, met: check.met(password) })),
    { label: "Both passwords match", met: password.length > 0 && password === confirm },
  ];
  const met = checks.filter((check) => check.met).length;

  return (
    <div className="space-y-4">
      <TextField label="Password" name="password" type="password" autoComplete="new-password"
        minLength={PASSWORD_MIN} maxLength={PASSWORD_MAX} required error={errors.password}
        aria-describedby="password-checklist" onChange={(event) => setPassword(event.currentTarget.value)} />
      <TextField label="Confirm password" name="confirmPassword" type="password" autoComplete="new-password" required
        error={errors.confirmPassword} onChange={(event) => setConfirm(event.currentTarget.value)} />
      <div id="password-checklist" className="space-y-1.5">
        <p className="sr-only" aria-live="polite">{met} of {checks.length} password requirements met</p>
        <ul className="grid gap-x-4 gap-y-1 text-xs sm:grid-cols-2">
          {checks.map((check) => (
            <li key={check.label}
              className={`flex items-center gap-2 transition-colors ${check.met ? "text-emerald-700 dark:text-emerald-400" : "text-muted"}`}>
              <span aria-hidden="true"
                className={`flex size-4 shrink-0 items-center justify-center rounded-full border ${check.met ? "border-emerald-600 bg-emerald-600 text-white dark:border-emerald-400 dark:bg-emerald-400 dark:text-ink" : "border-border"}`}>
                {check.met && (
                  <svg viewBox="0 0 12 12" className="size-2.5" fill="none" stroke="currentColor" strokeWidth="2"
                    strokeLinecap="round" strokeLinejoin="round"><path d="M2.5 6.2l2.3 2.3 4.7-5" /></svg>
                )}
              </span>
              <span>{check.label}<span className="sr-only">{check.met ? " (met)" : " (not yet)"}</span></span>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
