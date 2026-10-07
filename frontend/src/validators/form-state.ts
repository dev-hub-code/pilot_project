import type { z } from "zod";

export type FieldErrors = Partial<Record<string, string>>;

/** Result of a Server Action bound to a form through useActionState. */
export interface FormState {
  error?: string;
  success?: string;
  fieldErrors?: FieldErrors;
  /** Echoed back so the form keeps non-secret input after a failed submit. */
  values?: Record<string, string>;
}

export function firstErrors(error: z.ZodError): FieldErrors {
  const result: FieldErrors = {};
  for (const issue of error.issues) {
    const key = issue.path.join(".");
    result[key] ??= issue.message;
  }
  return result;
}
