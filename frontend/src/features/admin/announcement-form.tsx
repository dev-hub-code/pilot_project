"use client";

import { useConfirm } from "@/components/ui/confirm-dialog";
import { useActionState } from "react";
import { useFormStatus } from "react-dom";
import { Button } from "@/components/ui/button";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SelectField } from "@/components/ui/select-field";
import { TextArea } from "@/components/ui/text-area";
import { TextField } from "@/components/ui/text-field";
import type { AnnouncementAudience } from "@/types/announcement";
import type { FormState } from "@/validators/form-state";
import { publishAnnouncementAction } from "./announcement-actions";

const AUDIENCE_OPTION: Record<AnnouncementAudience, string> = {
  EVERYONE: "Everyone (investors and staff)",
  INVESTORS: "All investors (online and network)",
  ONLINE_INVESTORS: "Online investors only",
  NETWORK_INVESTORS: "Network investors only",
  STAFF: "Staff only",
};
const AUDIENCES = (Object.keys(AUDIENCE_OPTION) as AnnouncementAudience[]).map((value) => ({ value, label: AUDIENCE_OPTION[value] }));

/** Who the confirmation says it goes to. */
const RECIPIENTS: Record<AnnouncementAudience, string> = {
  EVERYONE: "every active investor and staff member",
  INVESTORS: "every active investor, online and network",
  ONLINE_INVESTORS: "every active online investor",
  NETWORK_INVESTORS: "every active network investor",
  STAFF: "every active staff member",
};

/** Writes and sends an announcement; it reaches everyone in the audience at once, so sending asks first. */
export function AnnouncementForm() {
  const [state, action] = useActionState<FormState, FormData>(publishAnnouncementAction, {});
  const e = state.fieldErrors ?? {};
  const v = state.values ?? {};

  return (
    <form action={action} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      <TextField label="Title" name="title" maxLength={200} defaultValue={v.title} error={e.title}
        placeholder="e.g. Scheduled maintenance on Sunday" />
      <TextArea label="Message" name="body" rows={5} maxLength={1000} defaultValue={v.body} error={e.body} />
      <div className="grid gap-4 sm:grid-cols-2">
        <SelectField label="Send to" name="audience" options={AUDIENCES} defaultValue={v.audience ?? "EVERYONE"}
          error={e.audience} />
        <TextField label="Link (optional)" name="link" maxLength={300} defaultValue={v.link} error={e.link}
          placeholder="/invest" />
      </div>
      <p className="text-xs text-muted">
        Every active account in the audience gets it in their notifications straight away; suspended and disabled
        accounts do not. It is not emailed, and it
        cannot be withdrawn once sent. A link opens that page of the site when the notification is clicked.
      </p>
      <SendButton />
    </form>
  );
}

function SendButton() {
  const { pending } = useFormStatus();
  const { ask, dialog } = useConfirm();
  return (
    <>
      <Button type="submit" disabled={pending}
        onClick={(event) => {
          const button = event.currentTarget;
          const form = button.form;
          if (!form) return;
          event.preventDefault();
          const audience = String(new FormData(form).get("audience") ?? "EVERYONE") as AnnouncementAudience;
          void ask(`Send this announcement to ${RECIPIENTS[audience] ?? RECIPIENTS.EVERYONE}? It cannot be withdrawn once sent.`, { confirmLabel: "Send" })
            .then((ok) => { if (ok) form.requestSubmit(button); });
        }}>
        {pending ? "Sending…" : "Send announcement"}
      </Button>
      {dialog}
    </>
  );
}
