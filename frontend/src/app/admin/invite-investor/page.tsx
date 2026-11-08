import type { Metadata } from "next";
import { Clock, KeyRound, MailCheck, Network, Send } from "lucide-react";
import { PageHeader } from "@/components/ui/page-header";
import { InviteInvestorForm } from "@/features/admin/invite-investor-form";

export const metadata: Metadata = { title: "Invite investor" };

/** What happens after an invitation is sent, in order. Must match how invitations work (see InviteInvestorForm). */
const STEPS = [
  { icon: Send, title: "You send the invitation", body: "The client gets an email with a link to open a network investor account." },
  { icon: Clock, title: "The link works once, for 7 days", body: "Sending another invitation to the same address replaces the earlier link." },
  { icon: KeyRound, title: "The client sets up their account", body: "They enter their own details and choose a password." },
  { icon: Network, title: "They earn on four levels", body: "Network investors earn referral commission every month on four levels." },
];

/** Open to every employee: the admin layout already admits only staff, and the API checks again. */
export default function InviteInvestorPage() {
  return (
    <div className="space-y-6">
      <PageHeader title="Invite investor"
        description="Email a client a link to open a network investor account. Network investors earn referral commission every month on four levels." />
      <div className="grid gap-6 lg:grid-cols-[1.4fr_1fr]">
        <section aria-labelledby="invite-form" className="overflow-hidden rounded-2xl border border-border bg-surface shadow-sm lg:self-start">
          <header className="flex items-center gap-3 border-b border-border/70 bg-background/40 px-5 py-4 sm:px-6">
            <span aria-hidden="true" className="flex size-10 items-center justify-center rounded-xl bg-gold/10 text-gold-text">
              <MailCheck className="size-5" />
            </span>
            <div>
              <h2 id="invite-form" className="font-semibold tracking-tight">Client details</h2>
              <p className="text-sm text-muted">Who the invitation goes to</p>
            </div>
          </header>
          <div className="p-5 sm:p-6"><InviteInvestorForm /></div>
        </section>

        <aside aria-labelledby="invite-how" className="relative isolate overflow-hidden rounded-2xl bg-ink p-6 text-on-ink shadow-xl shadow-ink/20 sm:p-7">
          <div aria-hidden="true" className="absolute -top-20 -right-20 -z-10 size-60 rounded-full bg-gold/30 blur-3xl" />
          <h2 id="invite-how" className="font-semibold tracking-tight">How the invitation works</h2>
          <ol className="relative mt-6 space-y-6 before:absolute before:top-2 before:bottom-2 before:left-[19px] before:w-px before:bg-on-ink/15">
            {STEPS.map(({ icon: Icon, title, body }) => (
              <li key={title} className="relative flex gap-4">
                <span aria-hidden="true" className="relative z-10 flex size-10 shrink-0 items-center justify-center rounded-xl border border-on-ink/15 bg-ink text-gold-on-ink">
                  <Icon className="size-[18px]" />
                </span>
                <span className="text-sm leading-relaxed">
                  <span className="block font-semibold">{title}</span>
                  <span className="block text-on-ink/70">{body}</span>
                </span>
              </li>
            ))}
          </ol>
        </aside>
      </div>
    </div>
  );
}
