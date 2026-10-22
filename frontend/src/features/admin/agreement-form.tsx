"use client";

import { labelOf, useConfirm } from "@/components/ui/confirm-dialog";
import { useActionState } from "react";
import { useFormStatus } from "react-dom";
import { Button } from "@/components/ui/button";
import { FormFeedback } from "@/components/ui/form-feedback";
import { TextArea } from "@/components/ui/text-area";
import { TextField } from "@/components/ui/text-field";
import { AgreementText } from "@/features/agreement/agreement-text";
import type { AgreementPlaceholder } from "@/types/agreement";
import { type AgreementFormState, agreementTemplateAction } from "./agreement-actions";

const SAVE_CONFIRM = "Save this wording? Agreements issued from now on use it; agreements already issued keep theirs.";

/** Edits the lease agreement's wording: insert placeholders, preview with example values, save as a new version. */
export function AgreementTemplateForm({ title, body, placeholders }: {
  title: string;
  body: string;
  placeholders: AgreementPlaceholder[];
}) {
  const [state, action] = useActionState<AgreementFormState, FormData>(agreementTemplateAction, {});
  const e = state.fieldErrors ?? {};
  const v = (name: string, fallback: string) => state.values?.[name] ?? fallback;

  /** Puts {{key}} into the wording where the cursor is. */
  function insert(key: string) {
    const area = document.getElementById("agreement-body") as HTMLTextAreaElement | null;
    if (!area) return;
    const token = `{{${key}}}`;
    const start = area.selectionStart ?? area.value.length;
    const end = area.selectionEnd ?? start;
    area.setRangeText(token, start, end, "end");
    area.focus();
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_18rem]">
      <form action={action} className="space-y-4" noValidate>
        <FormFeedback state={state} />
        <TextField label="Title" name="title" defaultValue={v("title", title)} error={e.title} />
        <TextArea id="agreement-body" label="Wording" name="body" rows={24} defaultValue={v("body", body)} error={e.body}
          className="w-full rounded-none border border-border bg-surface px-3 py-2 font-mono text-sm outline-none transition-colors focus:border-gold focus:ring-1 focus:ring-gold aria-invalid:border-rose-600" />
        <p className="text-xs text-muted">
          Leave a blank line between paragraphs. Start a line with <code className="font-mono"># </code> to make it a heading.
          Placeholders are replaced with each investment&apos;s details when the agreement is issued.
        </p>
        <div className="flex flex-wrap gap-3">
          <IntentButton intent="preview" variant="quiet">Preview</IntentButton>
          <IntentButton intent="save" confirm={SAVE_CONFIRM}>Save as new version</IntentButton>
        </div>
        {state.preview && (
          <section className="space-y-2" aria-label="Preview">
            <p className="text-xs uppercase tracking-[0.1em] text-muted">Preview with example values</p>
            <AgreementText title={state.preview.title} body={state.preview.body} />
          </section>
        )}
      </form>

      <aside className="space-y-3" aria-label="Placeholders">
        <p className="text-xs uppercase tracking-[0.1em] text-muted">Placeholders</p>
        <ul className="space-y-2 text-sm">
          {placeholders.map((p) => (
            <li key={p.key}>
              <button type="button" onClick={() => insert(p.key)}
                className="font-mono text-xs text-gold-text hover:underline" title="Insert at the cursor">
                {`{{${p.key}}}`}
              </button>
              <span className="block text-xs text-muted">{p.description} · e.g. {p.example}</span>
            </li>
          ))}
        </ul>
      </aside>
    </div>
  );
}

function IntentButton({ intent, variant, confirm, children }: {
  intent: "preview" | "save";
  variant?: "primary" | "quiet";
  confirm?: string;
  children: React.ReactNode;
}) {
  const { pending } = useFormStatus();
  const { ask, dialog } = useConfirm();
  return (
    <>
      <Button type="submit" name="intent" value={intent} variant={variant} disabled={pending}
        onClick={(event) => {
          const button = event.currentTarget;
          if (!confirm || !button.form) return;
          event.preventDefault();
          void ask(confirm, { confirmLabel: labelOf(children) }).then((ok) => { if (ok) button.form?.requestSubmit(button); });
        }}>
        {children}
      </Button>
      {dialog}
    </>
  );
}
