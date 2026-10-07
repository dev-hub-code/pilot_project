"use client";

import { Button } from "@/components/ui/button";

export default function AppError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <div className="border border-border bg-surface p-8 text-center">
      <h2 className="text-lg font-semibold">Something went wrong</h2>
      <p className="mt-1 text-sm text-muted">
        The action could not be completed. Please try again{error.digest ? ` (reference ${error.digest})` : ""}.
      </p>
      <Button className="mt-4" onClick={reset}>Try again</Button>
    </div>
  );
}
