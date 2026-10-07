import { Alert } from "./alert";
import { Notice } from "./notice";
import type { FormState } from "@/validators/form-state";

export function FormFeedback({ state }: { state: FormState }) {
  if (state.error) return <Alert>{state.error}</Alert>;
  if (state.success) return <Notice tone="success">{state.success}</Notice>;
  return null;
}
