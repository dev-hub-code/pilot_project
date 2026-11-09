"use client";

import { ChevronRight, Landmark, LoaderCircle, Smartphone, type LucideIcon } from "lucide-react";
import { useActionState } from "react";
import { useFormStatus } from "react-dom";
import { FormFeedback } from "@/components/ui/form-feedback";
import type { FormState } from "@/validators/form-state";

const ICONS: Record<"online" | "bank", LucideIcon> = { online: Smartphone, bank: Landmark };

/** One way to pay, drawn as a tile the investor clicks to start it. Reports a refusal inline. */
export function PayOption({ action, icon, title, hint, badge, pendingLabel = "Preparing payment…" }: {
  action: (state: FormState) => Promise<FormState>;
  icon: keyof typeof ICONS;
  title: string;
  hint: string;
  badge?: string;
  pendingLabel?: string;
}) {
  const [state, formAction] = useActionState<FormState>(action, {});
  return (
    <form action={formAction} className="space-y-2">
      <FormFeedback state={state} />
      <Tile icon={ICONS[icon]} title={title} hint={hint} badge={badge} pendingLabel={pendingLabel} />
    </form>
  );
}

function Tile({ icon: Icon, title, hint, badge, pendingLabel }: {
  icon: LucideIcon; title: string; hint: string; badge?: string; pendingLabel: string;
}) {
  const { pending } = useFormStatus();
  return (
    <button type="submit" disabled={pending}
      className="group flex w-full items-center gap-4 rounded-xl border border-border bg-background/40 p-4 text-left transition-all hover:-translate-y-0.5 hover:border-gold/60 hover:bg-gold/5 hover:shadow-md focus-visible:ring-2 focus-visible:ring-gold/40 focus-visible:outline-none disabled:cursor-wait disabled:opacity-70 motion-reduce:transition-none motion-reduce:hover:translate-y-0">
      <span aria-hidden="true"
        className="flex size-11 shrink-0 items-center justify-center rounded-xl bg-gold/10 text-gold-text transition-colors group-hover:bg-gold group-hover:text-white">
        {pending ? <LoaderCircle className="size-5 animate-spin" /> : <Icon className="size-5" />}
      </span>
      <span className="min-w-0 flex-1">
        <span className="flex flex-wrap items-center gap-2 text-sm font-semibold">
          {pending ? pendingLabel : title}
          {badge && !pending && (
            <span className="rounded-full bg-emerald-500/10 px-2 py-0.5 text-[11px] font-medium text-emerald-700 dark:text-emerald-300">
              {badge}
            </span>
          )}
        </span>
        <span className="mt-0.5 block text-xs text-muted">{hint}</span>
      </span>
      <ChevronRight aria-hidden="true" className="size-5 shrink-0 text-muted transition-transform group-hover:translate-x-0.5 group-hover:text-gold-text" />
    </button>
  );
}
