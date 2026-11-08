import { Alert } from "./alert";
import { Notice } from "./notice";
import type { FormState } from "@/validators/form-state";
import { KeepValues } from "./keep-values";

/** The result of a form's action; also keeps what was entered when that result is an error. */
export function FormFeedback({ state }: { state: FormState }) {
  return (
    <>
      <KeepValues state={state} />
      {state.error ? <Alert>{state.error}</Alert> : state.success ? <Notice tone="success">{state.success}</Notice> : null}
    </>
  );
}
