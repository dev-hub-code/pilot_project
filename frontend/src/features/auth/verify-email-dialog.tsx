"use client";

import { useEffect, useId, useRef } from "react";
import { Button } from "@/components/ui/button";
import { EmailText } from "@/components/ui/email-text";
import { ResendVerificationForm } from "./resend-verification-form";
import { VerifyCodeForm } from "./verify-code-form";

/**
 * Asks a signed-in user who has not confirmed their email address to do it now: the 6-digit code from the email,
 * or a new email. A modal <dialog> opened on arrival, once per sign-in; "Later" or Escape closes it. Once the code is
 * accepted the page re-renders without it.
 */
export function VerifyEmailDialog({ email }: { email: string }) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();

  useEffect(() => {
    const dialog = ref.current;
    if (dialog && !dialog.open) dialog.showModal();
    // Not again in this session; a failed request only means it may show once more.
    fetch("/dashboard/email-prompt", { method: "POST" }).catch(() => {});
    return () => dialog?.close();
  }, []);

  return (
    <dialog ref={ref} aria-labelledby={titleId}
      className="m-auto max-h-[calc(100dvh-2rem)] w-[min(28rem,calc(100vw-2rem))] overflow-y-auto border border-border bg-surface p-0 text-foreground shadow-2xl backdrop:bg-ink/60 backdrop:backdrop-blur-[2px] open:animate-[confirm-in_150ms_ease-out]">
      <div className="space-y-5 p-6">
        <div className="flex items-start gap-4">
          <span aria-hidden="true" className="flex size-10 shrink-0 items-center justify-center rounded-full bg-gold/15 text-gold-text">
            <svg viewBox="0 0 24 24" className="size-5" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <rect x="2" y="4" width="20" height="16" rx="2" /><path d="m22 7-10 6L2 7" />
            </svg>
          </span>
          <div className="min-w-0 space-y-1.5 pt-1">
            <h2 id={titleId} className="font-semibold">Confirm your email address</h2>
            <p className="text-sm leading-relaxed text-muted wrap-anywhere">
              We sent a 6-digit code to <span className="font-medium text-foreground"><EmailText>{email}</EmailText></span>.
              Enter it below, or open the link in that email.
            </p>
          </div>
        </div>
        <VerifyCodeForm email={email} stay />
        <div className="space-y-3 border-t border-border pt-5">
          <p className="text-sm text-muted">Nothing arrived, or the code expired? Check your spam folder, or send a new email.</p>
          <ResendVerificationForm email={email} />
        </div>
        <div className="flex justify-end">
          <Button type="button" variant="quiet" onClick={() => ref.current?.close()}>Later</Button>
        </div>
      </div>
    </dialog>
  );
}
