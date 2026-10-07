import type { Metadata } from "next";
import { Familjen_Grotesk, Geist_Mono, Instrument_Sans, Jost } from "next/font/google";
import { cookies, headers } from "next/headers";
import { parseTheme, THEME_COOKIE } from "@/lib/theme";
import "./globals.css";

const body = Instrument_Sans({ variable: "--font-body", subsets: ["latin"] });
const display = Familjen_Grotesk({ variable: "--font-display-face", subsets: ["latin"] });
const wordmark = Jost({ variable: "--font-wordmark-face", subsets: ["latin"], weight: ["300", "400"] });

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: {
    default: "SeaLease",
    template: "%s · SeaLease",
  },
  description: "Buy shipping containers on lease and receive rent plus your capital back every month.",
};

export default async function RootLayout({ children }: LayoutProps<"/">) {
  // Reading the request makes every page render per request, which the CSP nonce set by
  // proxy.ts requires (Next.js applies the nonce to its own scripts automatically).
  await headers();
  // Applied on the server, so the chosen theme is there on first paint (no flash).
  const theme = parseTheme((await cookies()).get(THEME_COOKIE)?.value);
  return (
    <html
      lang="en"
      data-theme={theme === "system" ? undefined : theme}
      className={`${body.variable} ${display.variable} ${wordmark.variable} ${geistMono.variable} h-full antialiased`}
    >
      <body className="min-h-full flex flex-col">{children}</body>
    </html>
  );
}
