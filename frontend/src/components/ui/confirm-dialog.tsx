"use client";

import { useId, useRef, useState } from "react";
import { Button } from "./button";

interface Question {
  message: string;
  /** What the confirm button says: the action itself ("Cancel order"), not "OK". */
  confirmLabel: string;
  danger: boolean;
  answer: (confirmed: boolean) => void;
}

/**
 * The app's own confirmation window, in place of the browser's `confirm()`: the question, the action named on the
 * confirm button (red for destructive actions) and "Go back". A modal <dialog>: focus stays inside, Escape and a
 * click outside dismiss it.
 *
 * `ask(...)` opens it and resolves to whether the user confirmed; render `dialog` once in the component.
 */
export function useConfirm() {
  const ref = useRef<HTMLDialogElement>(null);
  const [question, setQuestion] = useState<Question | null>(null);
  const titleId = useId();

  function ask(message: string, options: { confirmLabel?: string; danger?: boolean } = {}): Promise<boolean> {
    return new Promise((resolve) => {
      setQuestion({ message, confirmLabel: options.confirmLabel || "Confirm", danger: options.danger ?? false, answer: resolve });
      // Opened once React has rendered the question into it.
      requestAnimationFrame(() => ref.current?.showModal());
    });
  }

  function close(confirmed: boolean) {
    ref.current?.close();
    question?.answer(confirmed);
    setQuestion(null);
  }

  const dialog = (
    <dialog ref={ref} aria-labelledby={titleId}
      onCancel={(event) => { event.preventDefault(); close(false); }}
      onClick={(event) => { if (event.target === ref.current) close(false); }}
      className="m-auto w-[min(28rem,calc(100vw-2rem))] border border-border bg-surface p-0 text-foreground shadow-2xl backdrop:bg-ink/60 backdrop:backdrop-blur-[2px] open:animate-[confirm-in_150ms_ease-out]">
      {question && (
        <div className="space-y-5 p-6">
          <div className="flex items-start gap-4">
            <span aria-hidden="true"
              className={`flex size-10 shrink-0 items-center justify-center rounded-full ${
                question.danger ? "bg-rose-100 text-rose-700 dark:bg-rose-500/15 dark:text-rose-300" : "bg-gold/15 text-gold-text"}`}>
              <svg viewBox="0 0 24 24" className="size-5" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                {question.danger
                  ? <><path d="M12 9v4" /><path d="M12 17h.01" /><path d="M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z" /></>
                  : <><circle cx="12" cy="12" r="10" /><path d="M9.1 9a3 3 0 0 1 5.8 1c0 2-3 3-3 3" /><path d="M12 17h.01" /></>}
              </svg>
            </span>
            <div className="min-w-0 space-y-1.5 pt-1">
              <h2 id={titleId} className="font-semibold">Please confirm</h2>
              <p className="text-sm leading-relaxed text-muted wrap-anywhere">{question.message}</p>
            </div>
          </div>
          <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <Button type="button" variant="quiet" onClick={() => close(false)} autoFocus>Go back</Button>
            <Button type="button" variant={question.danger ? "danger" : "primary"} onClick={() => close(true)}>
              {question.confirmLabel}
            </Button>
          </div>
        </div>
      )}
    </dialog>
  );

  return { ask, dialog };
}

/** A button's text, to name the action on the confirm button; empty when it is not plain text. */
export function labelOf(children: React.ReactNode): string {
  if (typeof children === "string" || typeof children === "number") return String(children);
  if (Array.isArray(children)) return children.map(labelOf).join("");
  return "";
}
