import "server-only";
import { BackendError } from "./backend-client";
import type { FormState } from "@/validators/form-state";

/**
 * Converts a failed backend call into form state: field errors when the backend reports them,
 * otherwise its message for expected (4xx) failures, or a generic message with a support reference.
 */
export function failureState(error: unknown, values?: Record<string, string>): FormState {
  if (error instanceof BackendError) {
    const api = error.apiError;
    if (api?.fieldErrors?.length) {
      return {
        fieldErrors: Object.fromEntries(api.fieldErrors.map((f) => [f.field, f.message])),
        error: api.message,
        values,
      };
    }
    if (api && error.status < 500) return { error: api.message, values };
    return { error: `Something went wrong (reference ${error.correlationId}).`, values };
  }
  // Next.js navigation (redirect) is signalled by throwing; never swallow it.
  throw error;
}
