import { deniedUnlessInMenu } from "@/components/layout/page-access";
import type { Metadata } from "next";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { AnnouncementForm } from "@/features/admin/announcement-form";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import { AUDIENCE_LABEL, type Announcement, type AnnouncementAudience } from "@/types/announcement";
import { formatDateTime } from "@/utils/format";
import {
  Bell, BellOff, Briefcase, Globe, Link2, type LucideIcon, Megaphone, MailX, Network, Send, Undo2, UserRound, Users,
} from "lucide-react";

export const metadata: Metadata = { title: "Announcements" };

const AUDIENCE_ICON: Record<AnnouncementAudience, LucideIcon> = {
  EVERYONE: Users,
  INVESTORS: UserRound,
  ONLINE_INVESTORS: Globe,
  NETWORK_INVESTORS: Network,
  STAFF: Briefcase,
};

const RULES: { icon: LucideIcon; text: string }[] = [
  { icon: Bell, text: "Appears at once in each active account's notifications." },
  { icon: BellOff, text: "Suspended and disabled accounts do not get it." },
  { icon: MailX, text: "It is not emailed." },
  { icon: Undo2, text: "It cannot be withdrawn once sent, so read it twice." },
  { icon: Link2, text: "A link opens that page of the site when the notification is clicked." },
];

export default async function AnnouncementsPage({ searchParams }: PageProps<"/admin/announcements">) {
  const denied = await deniedUnlessInMenu("/admin/announcements");
  if (denied) return denied;
  await requireStaff();
  const page = Math.max(0, Number.parseInt(String((await searchParams).page ?? "0"), 10) || 0);
  const sent = await authFetch<PageResponse<Announcement>>(`/api/v1/admin/announcements?page=${page}&size=20`);

  return (
    <div className="space-y-6">
      <PageHeader title="Announcements"
        description="Send a message to everyone, all investors, online or network investors, or staff. It appears at once in each person's notifications." />

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_20rem]">
        <Card title="New announcement" className="min-w-0">
          <AnnouncementForm />
        </Card>
        <aside className="relative isolate overflow-hidden rounded-2xl bg-ink p-6 text-on-ink shadow-xl shadow-ink/20 lg:self-start">
          <div aria-hidden="true" className="absolute -top-20 -right-20 -z-10 size-56 rounded-full bg-gold/30 blur-3xl" />
          <span aria-hidden="true" className="flex size-11 items-center justify-center rounded-xl bg-on-ink/10 text-gold-on-ink ring-1 ring-on-ink/15">
            <Megaphone className="size-5" />
          </span>
          <h2 className="mt-4 font-semibold tracking-tight">Before you send</h2>
          <ul className="mt-4 space-y-3 text-sm">
            {RULES.map((rule) => (
              <li key={rule.text} className="flex gap-3">
                <rule.icon aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-gold-on-ink" />
                <span className="text-on-ink/80">{rule.text}</span>
              </li>
            ))}
          </ul>
        </aside>
      </div>

      <section className="space-y-4" aria-labelledby="sent-title">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 id="sent-title" className="flex items-center gap-3 text-lg font-semibold tracking-tight">
            <span aria-hidden="true" className="flex size-9 items-center justify-center rounded-xl bg-ink text-on-ink shadow-sm dark:bg-gold dark:text-on-gold">
              <Send className="size-[18px]" />
            </span>
            Sent
          </h2>
          {sent.totalElements > 0 && (
            <p className="text-sm text-muted">{sent.totalElements.toLocaleString("en-IN")} {sent.totalElements === 1 ? "announcement" : "announcements"}</p>
          )}
        </div>
        {sent.content.length === 0 ? (
          <EmptyState title="No announcements yet" description="Announcements you send appear here with how many people they reached." />
        ) : (
          <ol className="space-y-3">
            {sent.content.map((a) => {
              const Icon = AUDIENCE_ICON[a.audience];
              return (
                <li key={a.id}
                  className="group flex gap-4 rounded-2xl border border-border bg-surface p-4 shadow-sm transition duration-300 hover:border-gold/40 hover:shadow-md sm:p-5">
                  <span aria-hidden="true" className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-gold/10 text-gold-text transition-transform duration-300 group-hover:scale-105">
                    <Megaphone className="size-5" />
                  </span>
                  <div className="min-w-0 flex-1 space-y-2">
                    <div className="flex flex-wrap items-start justify-between gap-2">
                      <h3 className="min-w-0 font-semibold break-words">{a.title}</h3>
                      <span className="inline-flex shrink-0 items-center gap-1.5 rounded-full bg-background px-2.5 py-0.5 text-xs font-medium ring-1 ring-border">
                        <Icon aria-hidden="true" className="size-3.5 text-gold-text" /> {AUDIENCE_LABEL[a.audience]}
                      </span>
                    </div>
                    <p className="text-sm break-words whitespace-pre-line text-muted">{a.body}</p>
                    {a.link && (
                      <p className="inline-flex max-w-full items-center gap-1.5 rounded-lg bg-background/60 px-2 py-1 font-mono text-xs text-muted ring-1 ring-border">
                        <Link2 aria-hidden="true" className="size-3.5 shrink-0" /> <span className="break-all">{a.link}</span>
                      </p>
                    )}
                    <p className="flex flex-wrap items-center gap-x-4 gap-y-1 border-t border-border pt-2 text-xs text-muted">
                      <span className="inline-flex items-center gap-1 font-medium text-foreground">
                        <Users aria-hidden="true" className="size-3.5 text-gold-text" />
                        Reached {a.recipients.toLocaleString("en-IN")} {a.recipients === 1 ? "person" : "people"}
                      </span>
                      <span>{formatDateTime(a.createdAt)}</span>
                      {a.createdByName && <span>by {a.createdByName}</span>}
                    </p>
                  </div>
                </li>
              );
            })}
          </ol>
        )}
        <Pagination page={sent} basePath="/admin/announcements" params={{}} />
      </section>
    </div>
  );
}
