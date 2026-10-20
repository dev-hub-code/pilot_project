"use client";

import { useRef, useState, useSyncExternalStore } from "react";
import { Button } from "@/components/ui/button";
import { copyText } from "@/utils/clipboard";

const noSubscription = () => () => {};

/**
 * The investor's sign-up link, built from the page's own origin, with a copy button and, where the browser has a
 * share sheet (phones, mostly; HTTPS only), a share button that hands it to WhatsApp, SMS, email and other apps.
 */
export function ShareLink({ code }: { code: string }) {
  // The origin never changes while the page is open; on the server it is unknown, so render a relative link.
  const origin = useSyncExternalStore(noSubscription, () => window.location.origin, () => "");
  const canShare = useSyncExternalStore(noSubscription, () => typeof navigator.share === "function", () => false);
  const link = `${origin}/register?ref=${code}`;
  const [status, setStatus] = useState<"idle" | "copied" | "failed">("idle");
  const input = useRef<HTMLInputElement>(null);

  async function copy() {
    if (await copyText(link)) {
      setStatus("copied");
      setTimeout(() => setStatus("idle"), 2000);
    } else {
      // Clipboard blocked entirely: select the link so it can be copied by hand.
      input.current?.select();
      setStatus("failed");
    }
  }

  async function share() {
    try {
      await navigator.share({
        title: "Join me on SeaLease",
        text: `Own shipping containers on lease and receive rent every month. Sign up with my referral code ${code}:`,
        url: link,
      });
    } catch (error) {
      // Closing the share sheet is not a failure; anything else (e.g. sharing refused) falls back to copying.
      if ((error as DOMException).name !== "AbortError") await copy();
    }
  }

  return (
    <div className="flex flex-wrap items-center gap-3">
      <input ref={input} readOnly value={link} aria-label="Your referral link" onFocus={(e) => e.currentTarget.select()}
        className="h-11 min-w-0 flex-1 basis-full rounded-none border border-border bg-background px-3 font-mono text-sm sm:basis-0" />
      {canShare && <Button type="button" onClick={share}>Share</Button>}
      <Button type="button" variant="secondary" onClick={copy}>{status === "copied" ? "Copied" : "Copy link"}</Button>
      <span role="status" className={status === "failed" ? "w-full text-xs text-muted" : "sr-only"}>
        {status === "copied" ? "Link copied" : status === "failed" ? "Couldn't copy automatically. The link is selected: press Ctrl+C (⌘C on Mac)." : ""}
      </span>
    </div>
  );
}
