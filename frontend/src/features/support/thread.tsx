import type { TicketMessage } from "@/types/support";
import { formatDateTime } from "@/utils/format";
import { formatBytes } from "./labels";

/** A ticket conversation. {@code attachmentHref} builds the download link for the viewer's side. */
export function Thread({ messages, attachmentHref }: { messages: TicketMessage[]; attachmentHref: (attachmentId: string) => string }) {
  return (
    <ol className="space-y-3">
      {messages.map((m) => (
        <li key={m.id}
          className={`border px-4 py-3 text-sm ${m.internal ? "border-amber-300 bg-amber-50 dark:border-amber-500/40 dark:bg-amber-500/10"
            : m.fromStaff ? "border-border bg-background" : "border-border bg-surface"}`}>
          <p className="text-xs text-muted">
            <span className="font-medium text-foreground">{m.authorName ?? "—"}</span>
            {m.internal && <span className="ml-2 uppercase tracking-[0.08em] text-amber-700 dark:text-amber-300">Internal note</span>}
            {" · "}{formatDateTime(m.createdAt)}
          </p>
          <p className="mt-2 whitespace-pre-line">{m.body}</p>
          {m.attachments.length > 0 && (
            <ul className="mt-3 flex flex-wrap gap-2">
              {m.attachments.map((a) => (
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
