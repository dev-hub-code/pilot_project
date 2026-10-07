"use client";

import { useState, useSyncExternalStore } from "react";
import { Button } from "@/components/ui/button";

const noSubscription = () => () => {};

/** The investor's sign-up link, built from the page's own origin, with a copy button. */
export function ShareLink({ code }: { code: string }) {
  // The origin never changes while the page is open; on the server it is unknown, so render a relative link.
  const origin = useSyncExternalStore(noSubscription, () => window.location.origin, () => "");
  const link = `${origin}/register?ref=${code}`;
  const [copied, setCopied] = useState(false);

  async function copy() {
    try {
      await navigator.clipboard.writeText(link);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // Clipboard blocked (permissions, insecure origin): the link stays selectable.
    }
  }

  return (
    <div className="flex flex-wrap items-center gap-3">
      <input readOnly value={link} aria-label="Your referral link" onFocus={(e) => e.currentTarget.select()}
        className="h-11 min-w-0 flex-1 rounded-none border border-border bg-background px-3 font-mono text-sm" />
      <Button type="button" variant="secondary" onClick={copy}>{copied ? "Copied" : "Copy link"}</Button>
      <span role="status" className="sr-only">{copied ? "Link copied" : ""}</span>
    </div>
  );
}
