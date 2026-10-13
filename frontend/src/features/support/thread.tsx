import type { TicketEvent, TicketMessage } from "@/types/support";
import { formatDateTime } from "@/utils/format";
import { describeEvent, formatBytes } from "./labels";

type Entry = { kind: "message"; at: string; message: TicketMessage } | { kind: "event"; at: string; event: TicketEvent };

/**
 * A ticket conversation with its updates (status changes, reopenings, assignments) in between, oldest first.
 * {@code attachmentHref} builds the download link for the viewer's side.
 */
export function Thread({ messages, events = [], viewer, attachmentHref }: {
  messages: TicketMessage[];
  events?: TicketEvent[];
  viewer: "customer" | "staff";
  attachmentHref: (attachmentId: string) => string;
}) {
  const entries: Entry[] = [
    ...messages.map((message) => ({ kind: "message" as const, at: message.createdAt, message })),
    ...events.map((event) => ({ kind: "event" as const, at: event.createdAt, event })),
  ].sort((a, b) => a.at.localeCompare(b.at) || (a.kind === b.kind ? 0 : a.kind === "message" ? -1 : 1));

  return (
    <ol className="space-y-3">
      {entries.map((entry) => entry.kind === "event" ? (
        <li key={`e-${entry.event.id}`} className="flex items-center gap-3 px-1 text-xs text-muted">
          <span aria-hidden="true" className="size-2 shrink-0 rounded-full bg-gold" />
          <span>
            <span className="text-foreground">{describeEvent(entry.event, viewer)}</span>
            {" · "}{formatDateTime(entry.event.createdAt)}
          </span>
        </li>
      ) : (
        <li key={entry.message.id}
          className={`border px-4 py-3 text-sm ${entry.message.internal ? "border-amber-300 bg-amber-50 dark:border-amber-500/40 dark:bg-amber-500/10"
            : entry.message.fromStaff ? "border-border bg-background" : "border-border bg-surface"}`}>
          <p className="text-xs text-muted">
            <span className="font-medium text-foreground">{entry.message.authorName ?? "—"}</span>
            {entry.message.internal && <span className="ml-2 uppercase tracking-[0.08em] text-amber-700 dark:text-amber-300">Internal note</span>}
            {" · "}{formatDateTime(entry.message.createdAt)}
          </p>
          <p className="mt-2 whitespace-pre-line">{entry.message.body}</p>
          {entry.message.attachments.length > 0 && (
            <ul className="mt-3 flex flex-wrap gap-2">
              {entry.message.attachments.map((a) => (
                <li key={a.id}>
                  <a href={attachmentHref(a.id)} target="_blank" rel="noopener noreferrer"
                    className="inline-flex items-center gap-2 rounded-full border border-border px-3 py-1 text-xs hover:border-gold">
                    {a.filename} <span className="text-muted">{formatBytes(a.sizeBytes)}</span>
                  </a>
                </li>
              ))}
            </ul>
          )}
        </li>
      ))}
    </ol>
  );
}
