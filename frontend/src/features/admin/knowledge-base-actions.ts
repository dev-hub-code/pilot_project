"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { failureState } from "@/lib/server/action-errors";
import { authFetch } from "@/lib/server/auth/session";
import type { Article } from "@/types/support";
import type { FormState } from "@/validators/form-state";

const BASE = "/api/v1/admin/knowledge-base/articles";

function read(formData: FormData) {
  const tags = String(formData.get("tags") ?? "").split(",").map((t) => t.trim()).filter(Boolean);
  return {
    values: {
      title: String(formData.get("title") ?? "").trim(),
      summary: String(formData.get("summary") ?? "").trim(),
      body: String(formData.get("body") ?? ""),
      categoryCode: String(formData.get("categoryCode") ?? ""),
      visibility: String(formData.get("visibility") ?? "CUSTOMER"),
      tags: String(formData.get("tags") ?? ""),
      relatedIds: formData.getAll("relatedIds").map(String).join(","),
    },
    tags,
    relatedIds: formData.getAll("relatedIds").map(String).filter((id) => /^[0-9a-f-]{36}$/i.test(id)),
  };
}

function check(v: ReturnType<typeof read>["values"]): Record<string, string> {
  const e: Record<string, string> = {};
  if (v.title.length < 3) e.title = "Give the article a title";
  if (!v.summary) e.summary = "Write a one-line summary";
  if (!v.body.trim()) e.body = "Write the article";
  if (!v.categoryCode) e.categoryCode = "Choose a category";
  return e;
}

/** Creates a draft, or saves a new version of an article (refused if someone saved since it was opened). */
export async function saveArticleAction(articleId: string | null, version: number, _p: FormState, formData: FormData): Promise<FormState> {
  const { values, tags, relatedIds } = read(formData);
  const errors = check(values);
  if (Object.keys(errors).length > 0) return { fieldErrors: errors, values };
  const json = { title: values.title, summary: values.summary, body: values.body, categoryCode: values.categoryCode,
    visibility: values.visibility, tags, relatedIds };
  let saved: Article;
  try {
    saved = articleId
      ? await authFetch<Article>(`${BASE}/${articleId}?expectedVersion=${version}`, { method: "PUT", json })
      : await authFetch<Article>(BASE, { method: "POST", json });
  } catch (error) {
    return failureState(error, values);
  }
  revalidatePath("/platform/support/knowledge-base", "layout");
  if (!articleId) redirect(`/platform/support/knowledge-base/${saved.summary.id}`);
  return { success: `Saved as version ${saved.currentVersion}.` };
}

export async function articleStateAction(articleId: string, step: "publish" | "unpublish" | "archive"): Promise<FormState> {
  try {
    await authFetch(`${BASE}/${articleId}/${step}`, { method: "POST" });
  } catch (error) {
    return failureState(error);
  }
  revalidatePath("/platform/support/knowledge-base", "layout");
  return { success: step === "publish" ? "Published." : step === "unpublish" ? "Back to draft." : "Archived." };
}
