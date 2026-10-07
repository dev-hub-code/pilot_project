"use client";

import { useState, useTransition } from "react";
import type { Theme } from "@/lib/theme";
import { setThemeAction } from "./actions";

const OPTIONS: readonly { value: Theme; label: string; description: string }[] = [
  { value: "system", label: "System", description: "Match your device's light or dark setting" },
  { value: "light", label: "Light", description: "Always light" },
  { value: "dark", label: "Dark", description: "Always dark" },
];

/** Applies the theme at once, then remembers it on this browser. */
export function ThemePicker({ current }: { current: Theme }) {
  const [theme, setTheme] = useState<Theme>(current);
  const [saving, startSaving] = useTransition();

  function choose(next: Theme) {
    setTheme(next);
    applyTheme(next);
    startSaving(() => setThemeAction(next));
  }

  return (
    <fieldset className="space-y-3">
      <legend className="mb-1 text-sm font-medium">Theme</legend>
      <div className="grid gap-3 sm:grid-cols-3">
        {OPTIONS.map((option) => (
          <label key={option.value}
            className={`flex cursor-pointer items-start gap-3 border bg-surface p-4 text-sm transition-colors ${
              theme === option.value ? "border-gold" : "border-border hover:border-muted"}`}>
            <input type="radio" name="theme" value={option.value} checked={theme === option.value}
              onChange={() => choose(option.value)} className="mt-0.5 size-4" />
            <span>
              <span className="block font-medium">{option.label}</span>
              <span className="block text-xs text-muted">{option.description}</span>
            </span>
          </label>
        ))}
      </div>
      <p className="text-xs text-muted" aria-live="polite">{saving ? "Saving…" : "Remembered on this browser."}</p>
    </fieldset>
  );
}

/** Mirrors what the root layout renders from the cookie, so the change shows without a reload. */
function applyTheme(theme: Theme) {
  const root = document.documentElement;
  if (theme === "system") delete root.dataset.theme;
  else root.dataset.theme = theme;
}
