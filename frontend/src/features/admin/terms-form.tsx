"use client";

import { labelOf, useConfirm } from "@/components/ui/confirm-dialog";
import { useActionState, useState } from "react";
import { useFormStatus } from "react-dom";
import { Button } from "@/components/ui/button";
import { FormFeedback } from "@/components/ui/form-feedback";
import { TextArea } from "@/components/ui/text-area";
import { TextField } from "@/components/ui/text-field";
import { AgreementText } from "@/features/agreement/agreement-text";
import type { FormState } from "@/validators/form-state";
import { saveTermsAction } from "./terms-actions";

const SAVE_CONFIRM = "Save these terms? The Terms & conditions page shows them at once, and everyone signing up from now on accepts them. Existing accounts keep the version they accepted.";

/** Edits the terms and conditions: preview as the page shows them, save as a new version. */
export function TermsForm({ title, body }: { title: string; body: string }) {
  const [state, action] = useActionState<FormState, FormData>(saveTermsAction, {});
  const e = state.fieldErrors ?? {};
  const [draft, setDraft] = useState({ title: state.values?.title ?? title, body: state.values?.body ?? body });
  const [preview, setPreview] = useState(false);

  return (
    <form action={action} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      <TextField label="Title" name="title" value={draft.title} error={e.title}
        onChange={(event) => setDraft({ ...draft, title: event.target.value })} />
      <TextArea label="Wording" name="body" rows={24} value={draft.body} error={e.body}
        onChange={(event) => setDraft({ ...draft, body: event.target.value })}
        className="w-full rounded-none border border-border bg-surface px-3 py-2 font-mono text-sm outline-none transition-colors focus:border-gold focus:ring-1 focus:ring-gold aria-invalid:border-rose-600" />
      <p className="text-xs text-muted">
        Leave a blank line between paragraphs. Start a line with <code className="font-mono"># </code> to make it a heading.
      </p>
      <div className="flex flex-wrap gap-3">
        <Button type="button" variant="quiet" onClick={() => setPreview((p) => !p)}>
          {preview ? "Hide preview" : "Preview"}
        </Button>
        <SaveButton>Save as new version</SaveButton>
      </div>
      {preview && (
        <section className="space-y-2" aria-label="Preview">
          <p className="text-xs uppercase tracking-[0.1em] text-muted">Preview</p>
          <AgreementText title={draft.title} body={draft.body} />
        </section>
      )}
    </form>
  );
}

function SaveButton({ children }: { children: React.ReactNode }) {
  const { pending } = useFormStatus();
  const { ask, dialog } = useConfirm();
  return (
    <>
      <Button type="submit" disabled={pending}
        onClick={(event) => {
          const button = event.currentTarget;
          if (!button.form) return;
          event.preventDefault();
          void ask(SAVE_CONFIRM, { confirmLabel: labelOf(children) }).then((ok) => { if (ok) button.form?.requestSubmit(button); });
        }}>
        {children}
      </Button>
      {dialog}
    </>
  );
}
