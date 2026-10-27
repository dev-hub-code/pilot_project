"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useId, useRef, useState } from "react";
import { openNotificationAction, readAllNotificationsAction } from "@/features/notifications/actions";
import { notificationAction } from "@/features/notifications/labels";
import type { AppNotification } from "@/types/support";

/** How often the open, visible page checks for new notifications. */
const POLL_MS = 15_000;

/**
 * The current unread count, or null when it could not be read (offline, server restarting). A session that has ended
 * (signed in on another device, signed out elsewhere) leaves the page for sign-in: the poll is how an open page notices.
 */
async function fetchUnread(): Promise<number | null> {
  try {
    const response = await fetch("/notifications/unread", { cache: "no-store" });
    if (response.status === 401 || response.redirected) {
      // A full page load, as on sign-out: the client router's copies of signed-in pages go with it.
      // eslint-disable-next-line @next/next/no-location-assign-relative-destination
      window.location.assign("/session-expired");
      return null;
    }
    if (!response.ok) return null;
    const body = (await response.json()) as { unread: number | null };
    return typeof body.unread === "number" ? body.unread : null;
  } catch {
    return null;
  }
}

async function fetchRecent(): Promise<{ items: AppNotification[]; unread: number } | null> {
  try {
    const response = await fetch("/notifications/recent", { cache: "no-store" });
    return response.ok ? ((await response.json()) as { items: AppNotification[]; unread: number }) : null;
  } catch {
    return null;
  }
}

/** "just now", "5 min ago", "3 h ago", "2 d ago", then the date. */
function ago(iso: string, now: number): string {
  const minutes = Math.floor((now - Date.parse(iso)) / 60_000);
  if (minutes < 1) return "just now";
  if (minutes < 60) return `${minutes} min ago`;
  if (minutes < 24 * 60) return `${Math.floor(minutes / 60)} h ago`;
  if (minutes < 7 * 24 * 60) return `${Math.floor(minutes / (24 * 60))} d ago`;
  return new Date(iso).toLocaleDateString("en-IN", { day: "numeric", month: "short" });
}

/**
 * The header bell. Its unread badge stays current by polling while the tab is visible, and refreshing on
 * navigation and focus. Clicking it opens the latest notifications in a popup below the icon.
 */
export function NotificationBell({ initial }: { initial: number }) {
  const [unread, setUnread] = useState(initial);
  const [open, setOpen] = useState(false);
  const [recent, setRecent] = useState<{ items: AppNotification[]; loadedAt: number } | null>(null);
  const [failed, setFailed] = useState(false);
  const pathname = usePathname();
  // Close the popup once a notification has taken the user to its page. (Closing it on click would remove the
  // clicked notification's form before it is submitted, and the browser would drop the submission.)
  const [openedOn, setOpenedOn] = useState(pathname);
  if (pathname !== openedOn) {
    setOpenedOn(pathname);
    setOpen(false);
  }
  const root = useRef<HTMLDivElement>(null);
  const button = useRef<HTMLButtonElement>(null);
  const panelId = useId();

  // Badge: on every page, every POLL_MS while visible, and when the tab comes back.
  useEffect(() => {
    let active = true;
    const update = () => {
      void fetchUnread().then((count) => {
        if (active && count !== null) setUnread(count);
      });
    };
    const onVisible = () => {
      if (document.visibilityState === "visible") update();
    };
    update();
    const timer = setInterval(onVisible, POLL_MS);
    document.addEventListener("visibilitychange", onVisible);
    window.addEventListener("focus", onVisible);
    return () => {
      active = false;
      clearInterval(timer);
      document.removeEventListener("visibilitychange", onVisible);
      window.removeEventListener("focus", onVisible);
    };
  }, [pathname]);

  // Popup: load the latest when it opens and keep them fresh while it stays open.
  useEffect(() => {
    if (!open) return;
    let active = true;
    const load = () => {
      void fetchRecent().then((result) => {
        if (!active) return;
        setFailed(result === null);
        if (result) {
          setRecent({ items: result.items, loadedAt: Date.now() });
          setUnread(result.unread);
        }
      });
    };
    load();
    const timer = setInterval(load, POLL_MS);
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, [open]);

  // Close on outside click and Escape.
  useEffect(() => {
    if (!open) return;
    const onPointer = (event: PointerEvent) => {
      if (!root.current?.contains(event.target as Node)) setOpen(false);
    };
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setOpen(false);
        button.current?.focus();
      }
    };
    document.addEventListener("pointerdown", onPointer);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("pointerdown", onPointer);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  /** Shows a clicked notification as read straight away; its form marks it read on the server and opens its page. */
  function markRead(id: string) {
    if (!recent?.items.some((n) => n.id === id && !n.read)) return;
    setUnread((count) => Math.max(0, count - 1));
    setRecent((r) => r && { ...r, items: r.items.map((n) => (n.id === id ? { ...n, read: true } : n)) });
  }

  async function markAllRead() {
    await readAllNotificationsAction();
    setUnread(0);
    setRecent((r) => r && { ...r, items: r.items.map((n) => ({ ...n, read: true })) });
  }

  return (
    <div ref={root} className="relative">
      <button ref={button} type="button" onClick={() => setOpen((o) => !o)}
        aria-expanded={open} aria-controls={panelId} aria-haspopup="dialog"
        aria-label={unread > 0 ? `Notifications, ${unread} unread` : "Notifications"}
        className="relative flex size-10 items-center justify-center text-muted transition-colors hover:text-foreground focus-visible:ring-2 focus-visible:ring-gold focus-visible:outline-none">
        <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" className="size-5">
          <path d="M6 8a6 6 0 1 1 12 0c0 7 3 9 3 9H3s3-2 3-9" /><path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
        </svg>
        {unread > 0 && (
          <span className="absolute top-0.5 right-0 min-w-4 rounded-full bg-gold px-1 text-center text-[10px] leading-4 font-semibold text-ink">
            {unread > 99 ? "99+" : unread}
          </span>
        )}
      </button>
      {open && (
        <div id={panelId} role="dialog" aria-label="Notifications"
          className="fixed inset-x-4 top-[4.5rem] z-50 border border-border bg-surface shadow-lg sm:absolute sm:inset-x-auto sm:top-auto sm:right-0 sm:mt-2 sm:w-96">
          <div className="flex items-center justify-between gap-4 border-b border-border px-4 py-3">
            <p className="text-sm font-semibold">
              Notifications{unread > 0 && <span className="ml-2 text-xs font-normal text-muted">{unread} unread</span>}
            </p>
            {unread > 0 && (
              <button type="button" onClick={() => void markAllRead()} className="text-xs text-gold-text hover:underline">
                Mark all as read
              </button>
            )}
          </div>
          <div className="max-h-[60vh] overflow-y-auto">
            {recent === null ? (
              <p className="px-4 py-6 text-center text-sm text-muted">{failed ? "Couldn't load notifications." : "Loading…"}</p>
            ) : recent.items.length === 0 ? (
              <p className="px-4 py-6 text-center text-sm text-muted">You&apos;re all caught up.</p>
            ) : (
              <ul className="divide-y divide-border">
                {recent.items.map((n) => (
                  <li key={n.id}>
                    {/* Marks it read and opens what it points at. */}
                    <form action={openNotificationAction.bind(null, n.id, n.link)}>
                      <button type="submit" onClick={() => markRead(n.id)}
                        className="flex w-full items-start gap-3 px-4 py-3 text-left hover:bg-background">
                        <span aria-hidden="true" className={`mt-1.5 size-2 shrink-0 rounded-full ${n.read ? "bg-transparent" : "bg-gold"}`} />
                        <span className="min-w-0 flex-1">
                          <span className={`block text-sm ${n.read ? "text-muted" : "font-medium"}`}>{n.title}</span>
                          {n.body && <span className="block truncate text-xs text-muted">{n.body}</span>}
                          {notificationAction(n) && (
                            <span className="mt-1 block text-xs font-medium text-gold-text">{notificationAction(n)} →</span>
                          )}
                          <span className="mt-0.5 block text-[11px] text-muted">{ago(n.createdAt, recent.loadedAt)}</span>
                        </span>
                        {!n.read && <span className="sr-only">(unread)</span>}
                      </button>
                    </form>
                  </li>
                ))}
              </ul>
            )}
          </div>
          <Link href="/notifications" onClick={() => setOpen(false)}
            className="block border-t border-border px-4 py-3 text-center text-sm text-gold-text hover:bg-background">
            View all notifications
          </Link>
        </div>
      )}
    </div>
  );
}
