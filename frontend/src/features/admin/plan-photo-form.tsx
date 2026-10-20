"use client";

import { useActionState, useEffect, useRef, useState } from "react";
import { FileField } from "@/components/ui/file-field";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SubmitButton } from "@/components/ui/submit-button";
import type { FormState } from "@/validators/form-state";
import { PHOTO_TYPES } from "./plan-photos";

type Action = (state: FormState, formData: FormData) => Promise<FormState>;

/** Previews of the photos chosen in a file input (object URLs), before they are uploaded. */
export function ChosenPhotos({ urls }: { urls: string[] }) {
  if (urls.length === 0) return null;
  return (
    <ul className="grid grid-cols-4 gap-2 sm:grid-cols-6" aria-label="Chosen photos">
      {urls.map((url, i) => (
        <li key={url} className="aspect-[4/3] overflow-hidden bg-ink">
          {/* eslint-disable-next-line @next/next/no-img-element -- a local preview of the chosen file */}
          <img src={url} alt={`Chosen photo ${i + 1}`} className="size-full object-cover" />
        </li>
      ))}
    </ul>
  );
}

/** The file input for plan photos: several JPEG or PNG images at once, with previews. */
export function PlanPhotoInput({ room, error, required }: { room: number; error?: string; required?: boolean }) {
  const [urls, setUrls] = useState<string[]>([]);
  // Object URLs are freed when the choice changes (below) and when the form goes away.
  const made = useRef<string[]>([]);
  useEffect(() => () => made.current.forEach((u) => URL.revokeObjectURL(u)), []);
  return (
    <div className="space-y-2">
      <FileField label="Container photos" id="plan-photos" name="photos" accept={PHOTO_TYPES} multiple required={required} error={error}
        hint={`JPEG or PNG, up to 5 MB each; up to ${room} more`}
        onChange={(e) => {
          made.current.forEach((u) => URL.revokeObjectURL(u));
          made.current = Array.from(e.target.files ?? []).map((f) => URL.createObjectURL(f));
          setUrls(made.current);
        }} />
      {urls.length > 0 && <p className="text-xs text-muted">{urls.length} chosen; the first is the cover if the listing has none yet.</p>}
      <ChosenPhotos urls={urls} />
    </div>
  );
}

/** Uploads one or more photos for an existing plan. */
export function PlanPhotoForm({ action, room }: { action: Action; room: number }) {
  // React empties the file input after every submit, so the previews start afresh too (a new key each round).
  const [state, formAction] = useActionState<FormState & { round?: number }, FormData>(
    async (previous, formData) => ({ ...(await action(previous, formData)), round: (previous.round ?? 0) + 1 }), {});
  return (
    <form action={formAction} className="space-y-3" noValidate>
      <FormFeedback state={state} />
      <PlanPhotoInput key={state.round ?? 0} room={room} required error={state.fieldErrors?.photos} />
      <SubmitButton variant="secondary" pendingLabel="Uploading…">Upload photos</SubmitButton>
    </form>
  );
}
