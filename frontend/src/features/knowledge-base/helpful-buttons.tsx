"use client";

import { useState, useTransition } from "react";
import { Button } from "@/components/ui/button";
import type { FormState } from "@/validators/form-state";

/** "Did this help?" with yes and no; the server keeps one answer per reader. */
export function HelpfulButtons({ vote }: { vote: (helpful: boolean) => Promise<FormState> }) {
  const [state, setState] = useState<FormState>({});
  const [pending, start] = useTransition();
  if (state.success) return <p className="text-sm text-muted" role="status">{state.success}</p>;
  return (
    <div className="flex flex-wrap items-center gap-3">
      <span className="text-sm font-medium">Did this answer your question?</span>
      <Button type="button" variant="secondary" disabled={pending} onClick={() => start(async () => setState(await vote(true)))}>Yes</Button>
      <Button type="button" variant="secondary" disabled={pending} onClick={() => start(async () => setState(await vote(false)))}>No</Button>
      {state.error && <p className="w-full text-sm text-rose-600 dark:text-rose-400" role="alert">{state.error}</p>}
    </div>
  );
}
