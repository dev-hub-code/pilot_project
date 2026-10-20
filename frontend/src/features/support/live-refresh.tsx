"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";

const POLL_MS = 5_000;

/**
 * Keeps an open conversation live: re-renders the page's server data every POLL_MS while the tab is visible, and as
 * soon as it comes back, so the other side's messages appear without a reload. A half-typed reply is kept (the
 * refresh only replaces server-rendered parts; the reply form's state stays).
 */
export function LiveRefresh() {
  const router = useRouter();
  useEffect(() => {
    const onVisible = () => {
      if (document.visibilityState === "visible") router.refresh();
    };
    const timer = setInterval(onVisible, POLL_MS);
    document.addEventListener("visibilitychange", onVisible);
    window.addEventListener("focus", onVisible);
    return () => {
      clearInterval(timer);
      document.removeEventListener("visibilitychange", onVisible);
      window.removeEventListener("focus", onVisible);
    };
  }, [router]);
  return null;
}
