"use client";

import { Button } from "@/components/ui/button";

export default function AdminError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <div className="border border-border bg-surface p-8 text-center">
      <h2 className="text-lg font-semibold">This page could not be loaded</h2>
      <p className="mt-1 text-sm text-muted">
        You may lack the required permission, or the service is unavailable{error.digest ? ` (reference ${error.digest})` : ""}.
      </p>
      <Button className="mt-4" onClick={reset}>Try again</Button>
    </div>
  );
}
