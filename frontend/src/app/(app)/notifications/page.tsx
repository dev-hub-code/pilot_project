import type { Metadata } from "next";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { SubmitButton } from "@/components/ui/submit-button";
import { openNotificationAction, readAllNotificationsAction } from "@/features/notifications/actions";
import { authFetch } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { AppNotification } from "@/types/support";
import { formatDateTime } from "@/utils/format";

export const metadata: Metadata = { title: "Notifications" };

export default async function NotificationsPage({ searchParams }: PageProps<"/notifications">) {
  const page = Math.max(0, Number.parseInt(String((await searchParams).page ?? "0"), 10) || 0);
  const notifications = await authFetch<PageResponse<AppNotification>>(`/api/v1/notifications?page=${page}&size=20`);
  const anyUnread = notifications.content.some((n) => !n.read);
  return (
    <div className="space-y-6">
      <PageHeader title="Notifications"
        actions={anyUnread ? (
          <form action={readAllNotificationsAction}><SubmitButton variant="secondary">Mark all as read</SubmitButton></form>
        ) : undefined} />
      {notifications.content.length === 0 ? (
        <EmptyState title="You're all caught up" />
      ) : (
        <ul className="divide-y divide-border border border-border bg-surface">
          {notifications.content.map((n) => (
            <li key={n.id}>
              <form action={openNotificationAction.bind(null, n.id, n.link)}>
                <button type="submit" className="flex w-full items-start gap-3 px-4 py-3 text-left hover:bg-background">
                  <span aria-hidden="true" className={`mt-1.5 size-2 shrink-0 rounded-full ${n.read ? "bg-transparent" : "bg-gold"}`} />
                  <span className="min-w-0 flex-1">
                    <span className={`block text-sm ${n.read ? "text-muted" : "font-medium"}`}>{n.title}</span>
                    {n.body && <span className="block truncate text-xs text-muted">{n.body}</span>}
                  </span>
                  <span className="shrink-0 text-xs text-muted">{formatDateTime(n.createdAt)}</span>
                  {!n.read && <span className="sr-only">(unread)</span>}
                </button>
              </form>
            </li>
          ))}
        </ul>
      )}
      <Pagination page={notifications} basePath="/notifications" params={{}} />
    </div>
  );
}
