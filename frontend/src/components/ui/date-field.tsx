"use client";

import { useRef, useState } from "react";
import { formatDayMonthYear, maskDayMonthYear, parseDayMonthYear } from "@/utils/format";

interface DateFieldProps {
  label: string;
  name: string;
  /** As shown and submitted: DD/MM/YYYY. */
  defaultValue?: string;
  error?: string;
  /** Latest selectable date in the calendar, YYYY-MM-DD. */
  max?: string;
  autoComplete?: string;
}

/**
 * A date typed as DD/MM/YYYY (the slashes are added while typing), or picked from the browser's calendar via the button. The calendar shows dates in the
 * browser's own format, so the field itself always carries the DD/MM/YYYY text.
 *
 * <p>The calendar is a transparent native date input laid over the button, so tapping the button on a phone opens the
 * phone's own date picker (iOS Safari has no showPicker()); a click elsewhere, or the button from the keyboard, opens
 * it with showPicker().
 */
export function DateField({ label, name, defaultValue = "", error, max, autoComplete }: DateFieldProps) {
  const [text, setText] = useState(defaultValue);
  const picker = useRef<HTMLInputElement>(null);
  const inputId = name;
  const errorId = `${inputId}-error`;

  function openPicker() {
    const input = picker.current;
    if (!input) return;
    try {
      input.showPicker();
    } catch {
      // Browsers without showPicker(): focus the date input so its own control opens.
      input.focus();
    }
  }

  return (
    <div className="space-y-1.5">
      <label htmlFor={inputId} className="block text-sm font-medium text-foreground">
        {label}
      </label>
      <div className="relative flex">
        {/* Formatted while typing at the end; a correction in the middle is left as typed, so the caret stays put.
            No maxLength: a keyboard or paste can send "15/06" then "/1990", briefly 11 characters before formatting. */}
        <input id={inputId} name={name} value={text}
          onChange={(event) => {
            const input = event.target;
            const atEnd = input.selectionStart === null || input.selectionStart >= input.value.length;
            setText(atEnd ? maskDayMonthYear(input.value, text) : input.value);
          }}
          placeholder="DD/MM/YYYY" inputMode="numeric" autoComplete={autoComplete}
          aria-invalid={error ? true : undefined} aria-describedby={error ? errorId : undefined}
          className="h-11 w-full rounded-none border border-border bg-surface px-3 text-sm outline-none transition-colors focus:border-gold focus:ring-1 focus:ring-gold aria-invalid:border-rose-600" />
        <button type="button" onClick={openPicker} aria-label={`Choose ${label.toLowerCase()} from a calendar`}
          className="flex h-11 w-11 shrink-0 items-center justify-center border border-l-0 border-border bg-surface text-muted transition-colors hover:text-foreground focus-visible:ring-1 focus-visible:ring-gold focus-visible:outline-none">
          <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" className="size-5">
            <rect x="3" y="5" width="18" height="16" rx="1" /><path d="M3 10h18M8 3v4M16 3v4" />
          </svg>
        </button>
        {/* The browser's calendar, over the button; picking a date fills the text field. */}
        <input ref={picker} type="date" tabIndex={-1} aria-hidden="true" max={max}
          value={parseDayMonthYear(text) ?? ""}
          onClick={openPicker}
          onChange={(event) => setText(formatDayMonthYear(event.target.value))}
          className="absolute inset-y-0 right-0 h-11 w-11 cursor-pointer opacity-0" />
      </div>
      {error && (
        <p id={errorId} className="text-xs text-rose-600 dark:text-rose-400">
          {error}
        </p>
      )}
    </div>
  );
}
