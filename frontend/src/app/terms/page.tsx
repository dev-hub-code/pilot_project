import type { Metadata } from "next";
import { Eyebrow } from "@/components/brand/eyebrow";
import { Logo } from "@/components/brand/logo";
import { backendFetch } from "@/lib/server/backend-client";
import type { TermsVersion } from "@/types/terms";
import { formatDate } from "@/utils/format";

export const metadata: Metadata = {
  title: "Terms & Conditions",
  description: "The terms of using SeaLease, which everyone accepts when they create an account.",
};

// Super admins change the terms at any time: always show the version in force.
export const dynamic = "force-dynamic";

/** The terms and conditions in force, as super admins wrote them; linked from sign-up and the landing page footer. */
export default async function TermsPage() {
  const terms = await backendFetch<TermsVersion>("/api/v1/public/terms").catch(() => null);
  return (
    <div className="flex flex-1 bg-frame p-2 sm:p-5 lg:p-10">
      <div className="mx-auto w-full max-w-4xl bg-surface px-5 py-10 sm:px-10 lg:px-14 lg:py-14">
        <Logo />
        {terms ? (
          <>
            <div className="mt-12 space-y-4">
              <Eyebrow>Terms & Conditions</Eyebrow>
              <h1 className="text-3xl font-semibold tracking-tight sm:text-4xl">{terms.title}</h1>
              <p className="text-sm text-muted">Version {terms.version} · in force since {formatDate(terms.createdAt)}</p>
            </div>
            <article className="mt-10 max-w-3xl space-y-4 text-sm leading-relaxed">
              {blocks(terms.body ?? "").map((b, i) => b.heading
                ? <h2 key={i} className="pt-4 text-lg font-semibold tracking-tight">{b.text}</h2>
                : <p key={i} className="whitespace-pre-line text-foreground/90">{b.text}</p>)}
            </article>
          </>
        ) : (
          <p className="mt-12 text-sm text-muted">The terms and conditions could not be loaded. Please try again shortly.</p>
        )}
      </div>
    </div>
  );
}

/** A blank line separates paragraphs; a line starting with "# " is a heading (as in the admin editor). */
function blocks(body: string): { heading: boolean; text: string }[] {
  const out: { heading: boolean; text: string }[] = [];
  for (const block of body.replace(/\r\n/g, "\n").split(/\n\s*\n/)) {
    let text = block.trim();
    if (!text) continue;
    if (text.startsWith("# ")) {
      const end = text.indexOf("\n");
      out.push({ heading: true, text: (end < 0 ? text : text.slice(0, end)).slice(2).trim() });
      text = end < 0 ? "" : text.slice(end + 1).trim();
      if (!text) continue;
    }
    out.push({ heading: false, text });
  }
  return out;
}
