import type { Metadata } from "next";
import { cookies } from "next/headers";
import { ThemePicker } from "@/features/settings/theme-picker";
import { parseTheme, THEME_COOKIE } from "@/lib/theme";

export const metadata: Metadata = { title: "Appearance" };

export default async function AppearancePage() {
  const theme = parseTheme((await cookies()).get(THEME_COOKIE)?.value);
  return <ThemePicker current={theme} />;
}
