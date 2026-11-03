"use client";

import { useActionState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SelectField } from "@/components/ui/select-field";
import { SubmitButton } from "@/components/ui/submit-button";
import type { Article, ArticleSummary, KbCategory } from "@/types/support";
import type { FormState } from "@/validators/form-state";

type Action = (state: FormState, formData: FormData) => Promise<FormState>;

const input = "w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm outline-none focus:border-gold focus:ring-1 focus:ring-gold";

function FieldError({ text }: { text?: string }) {
  return text ? <p className="text-xs text-rose-600 dark:text-rose-400">{text}</p> : null;
}

/** Title, summary, body (light Markdown, shown as plain text), category, audience, tags and related articles. */
export function ArticleEditor({ action, article, categories, candidates }: {
  action: Action;
  article: Article | null;
  categories: KbCategory[];
  candidates: ArticleSummary[];
}) {
  const [state, formAction] = useActionState<FormState, FormData>(action, {});
  const v = state.values;
  const e = state.fieldErrors ?? {};
  const a = article?.summary;
  const related = new Set((v?.relatedIds ?? article?.related.map((r) => r.id).join(",") ?? "").split(",").filter(Boolean));
  return (
    <form action={formAction} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      <div className="space-y-1.5">
        <label htmlFor="title" className="block text-sm font-medium">Title</label>
        <input id="title" name="title" maxLength={200} defaultValue={v?.title ?? a?.title} className={input} aria-invalid={e.title ? true : undefined} />
        <FieldError text={e.title} />
      </div>
      <div className="space-y-1.5">
        <label htmlFor="summary" className="block text-sm font-medium">Summary</label>
        <input id="summary" name="summary" maxLength={300} defaultValue={v?.summary ?? a?.summary} className={input} aria-invalid={e.summary ? true : undefined} />
        <FieldError text={e.summary} />
      </div>
      <div className="grid gap-4 sm:grid-cols-3">
        <SelectField label="Category" name="categoryCode" placeholder="Choose…" defaultValue={v?.categoryCode ?? a?.categoryCode ?? ""}
          options={categories.map((c) => ({ value: c.code, label: c.name }))} error={e.categoryCode} />
        <SelectField label="Who can read it" name="visibility" defaultValue={v?.visibility ?? a?.visibility ?? "CUSTOMER"}
          options={[
            { value: "PUBLIC", label: "Public: anyone, signed in or not" },
            { value: "CUSTOMER", label: "Customers: signed-in investors" },
            { value: "INTERNAL", label: "Internal: employees only" },
          ]} />
        <div className="space-y-1.5">
          <label htmlFor="tags" className="block text-sm font-medium">Tags</label>
          <input id="tags" name="tags" defaultValue={v?.tags ?? a?.tags.join(", ")} placeholder="comma, separated" className={input} />
        </div>
      </div>
      <div className="space-y-1.5">
        <label htmlFor="body" className="block text-sm font-medium">Article</label>
        <textarea id="body" name="body" rows={18} maxLength={100000} defaultValue={v?.body ?? article?.body}
          className={`${input} font-mono`} aria-invalid={e.body ? true : undefined} />
        <p className="text-xs text-muted">Blank lines separate paragraphs; “## ” starts a heading, “- ” a bullet and “1. ” a numbered step. HTML is shown as text, never run.</p>
        <FieldError text={e.body} />
      </div>
      {candidates.length > 0 && (
        <fieldset className="space-y-2">
          <legend className="text-sm font-medium">Related articles (up to 10)</legend>
          <div className="grid max-h-48 gap-1 overflow-y-auto rounded-lg border border-border p-2 text-sm sm:grid-cols-2">
            {candidates.filter((c) => c.id !== a?.id).map((c) => (
              <label key={c.id} className="flex items-center gap-2">
                <input type="checkbox" name="relatedIds" value={c.id} defaultChecked={related.has(c.id)} className="size-4" />
                <span className="truncate">{c.title}</span>
              </label>
            ))}
          </div>
        </fieldset>
      )}
      <SubmitButton>{article ? "Save new version" : "Create draft"}</SubmitButton>
    </form>
  );
}
