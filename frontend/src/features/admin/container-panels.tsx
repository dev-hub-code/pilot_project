"use client";

import { useActionState } from "react";
import { FileField } from "@/components/ui/file-field";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SelectField } from "@/components/ui/select-field";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import type { ContainerStatus } from "@/types/marketplace";
import type { FormState } from "@/validators/form-state";
import { changeContainerStatusAction, uploadContainerDocumentAction } from "./investment-actions";

const PURPOSES = [
  { value: "CONTAINER_PHOTO", label: "Photo" },
  { value: "CONTAINER_SURVEY_REPORT", label: "Survey report" },
  { value: "LEASE_AGREEMENT", label: "Lease agreement" },
  { value: "INSURANCE_CERTIFICATE", label: "Insurance certificate" },
  { value: "OFFERING_MEMORANDUM", label: "Offering memorandum" },
];

const STATUSES = [
  { value: "AVAILABLE", label: "Available" },
  { value: "MAINTENANCE", label: "Maintenance" },
  { value: "RETIRED", label: "Retired (permanent)" },
];

export function DocumentUploadForm({ containerId }: { containerId: string }) {
  const [state, action] = useActionState<FormState, FormData>(uploadContainerDocumentAction.bind(null, containerId), {});
  const e = state.fieldErrors ?? {};
  return (
    <form action={action} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      <div className="grid gap-4 sm:grid-cols-2">
        <SelectField label="Type" name="purpose" options={PURPOSES} defaultValue={state.values?.purpose ?? "CONTAINER_PHOTO"} error={e.purpose} />
        <TextField label="Title" name="title" placeholder="Side view, Rotterdam" defaultValue={state.values?.title} error={e.title} />
      </div>
      <FileField label="File" name="file" accept="application/pdf,image/jpeg,image/png" hint="Photos: JPEG/PNG. Documents: PDF. Max 5 MB."
        error={e.file} required />
      <label className="flex items-center gap-2 text-sm">
        <input type="checkbox" name="visibleToInvestors" defaultChecked className="size-4" />
        Visible to investors in the marketplace
      </label>
      <SubmitButton variant="quiet" pendingLabel="Uploading…">Upload</SubmitButton>
    </form>
  );
}

export function ContainerStatusForm({ containerId, current }: { containerId: string; current: ContainerStatus }) {
  const [state, action] = useActionState<FormState, FormData>(changeContainerStatusAction.bind(null, containerId), {});
  const e = state.fieldErrors ?? {};
  return (
    <form action={action} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      <SelectField label="New status" name="status" options={STATUSES.filter((s) => s.value !== current)} error={e.status} />
      <TextField label="Reason" name="reason" error={e.reason} />
      <SubmitButton variant="quiet" confirm="Change the container status?">Update status</SubmitButton>
    </form>
  );
}
