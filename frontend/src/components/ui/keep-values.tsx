"use client";

import { useEffect, useLayoutEffect, useRef } from "react";

type Field = HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement;
type Saved = { value: string; checked: boolean; files: File[] };

/** Inputs whose value comes from the page itself, not from what the user typed. */
const SKIP = new Set(["hidden", "submit", "button", "reset", "image"]);

/**
 * Keeps what the user entered when a submit comes back with an error.
 *
 * React clears a form after its action finishes, even when the server rejects the input (a file over 2 MB, a
 * mistyped PAN): everything the user typed or picked would be gone. Rendered inside a form, this remembers each
 * field at submit and puts it back when the result is an error. On success the form clears as before. The
 * sign-in password is left empty on purpose.
 */
export function KeepValues({ state }: { state: { error?: string; fieldErrors?: Partial<Record<string, string>> } }) {
  const anchor = useRef<HTMLSpanElement>(null);
  const saved = useRef<Map<Field, Saved> | null>(null);
  const failed = useRef(false);

  useEffect(() => {
    // Usually inside the form; when placed just above it, the form beside it.
    const form = anchor.current?.closest("form") ?? anchor.current?.parentElement?.querySelector("form");
    if (!form) return;
    const onSubmit = () => {
      saved.current = snapshot(form);
      failed.current = false;
    };
    // The clearing fires a reset event and happens just after it: put the values back once it is done.
    const onReset = () => {
      setTimeout(() => {
        if (failed.current && saved.current) restore(saved.current);
        failed.current = false;
      });
    };
    form.addEventListener("submit", onSubmit, true);
    form.addEventListener("reset", onReset);
    return () => {
      form.removeEventListener("submit", onSubmit, true);
      form.removeEventListener("reset", onReset);
    };
  }, []);

  useLayoutEffect(() => {
    if (!saved.current) return;
    const hasError = Boolean(state.error) || Object.values(state.fieldErrors ?? {}).some(Boolean);
    if (hasError) {
      failed.current = true;
      restore(saved.current);
    } else {
      saved.current = null;
      failed.current = false;
    }
  }, [state]);

  return <span ref={anchor} hidden />;
}

function snapshot(form: HTMLFormElement): Map<Field, Saved> {
  const values = new Map<Field, Saved>();
  for (const el of Array.from(form.elements)) {
    if (!isField(el) || !el.name || el.disabled) continue;
    if (el instanceof HTMLInputElement) {
      if (SKIP.has(el.type) || el.autocomplete === "current-password") continue;
      values.set(el, { value: el.value, checked: el.checked, files: el.files ? Array.from(el.files) : [] });
    } else {
      values.set(el, { value: el.value, checked: false, files: [] });
    }
  }
  return values;
}

function restore(values: Map<Field, Saved>) {
  for (const [el, s] of values) {
    if (!el.isConnected) continue;
    if (el instanceof HTMLInputElement && (el.type === "checkbox" || el.type === "radio")) {
      // A click updates both the box and any React state that follows it.
      if (el.checked !== s.checked && (el.type === "checkbox" || s.checked)) el.click();
    } else if (el instanceof HTMLInputElement && el.type === "file") {
      if (s.files.length > 0 && (el.files?.length ?? 0) === 0) {
        const transfer = new DataTransfer();
        for (const file of s.files) transfer.items.add(file);
        el.files = transfer.files;
        el.dispatchEvent(new Event("change", { bubbles: true }));
      }
    } else if (el.value !== s.value) {
      // Set through the native setter so fields React controls see the change too.
      const proto = el instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype
        : el instanceof HTMLSelectElement ? HTMLSelectElement.prototype : HTMLInputElement.prototype;
      Object.getOwnPropertyDescriptor(proto, "value")?.set?.call(el, s.value);
      el.dispatchEvent(new Event("input", { bubbles: true }));
      el.dispatchEvent(new Event("change", { bubbles: true }));
    }
  }
}

function isField(el: Element): el is Field {
  return el instanceof HTMLInputElement || el instanceof HTMLTextAreaElement || el instanceof HTMLSelectElement;
}
