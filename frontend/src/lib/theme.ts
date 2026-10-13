/** The colour theme chosen in Settings; "system" follows the operating system. */
export const THEMES = ["system", "light", "dark"] as const;
export type Theme = (typeof THEMES)[number];

export const THEME_COOKIE = "sl_theme";

/** Light until the visitor chooses otherwise in Settings (e.g. the sign-in pages, and anyone who has not chosen). */
export const DEFAULT_THEME: Theme = "light";

export function parseTheme(value: string | undefined | null): Theme {
  return THEMES.includes(value as Theme) ? (value as Theme) : DEFAULT_THEME;
}
