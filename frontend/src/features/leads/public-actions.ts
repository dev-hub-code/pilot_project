"use server";

import { headers } from "next/headers";
import { BackendError, backendFetch } from "@/lib/server/backend-client";
import { forwardedClientHeaders } from "@/lib/server/request-context";
import { type FormState, firstErrors } from "@/validators/form-state";
import { interestSchema } from "@/validators/lead";

/**
 * The public interest form. Not authenticated: the backend rate-limits by the forwarded client
 * address and answers the same way whatever it did with the data.
 */
export async function submitInterestAction(_p: FormState, formData: FormData): Promise<FormState> {
  const input: Record<string, string> = {};
  formData.forEach((value, key) => {
    if (typeof value === "string" && !key.startsWith("$")) input[key] = value;
  });
  const parsed = interestSchema.safeParse(input);
  if (!parsed.success) return { fieldErrors: firstErrors(parsed.error), values: input };
  try {
    await backendFetch("/api/v1/public/leads", {
      method: "POST",
      // The checkbox posts "on"; the schema has already required it.
      json: { ...parsed.data, consent: true },
      headers: forwardedClientHeaders(await headers()),
    });
  } catch (error) {
    if (error instanceof BackendError && error.status === 429) {
      return { error: "Too many requests from your network. Please try again later.", values: input };
    }
    if (error instanceof BackendError && error.status === 400) {
      return { error: "Please check the form and try again.", values: input };
    }
    return { error: "We could not send your message. Please try again later.", values: input };
  }
  return { success: "Thank you. Our team will be in touch shortly." };
}
