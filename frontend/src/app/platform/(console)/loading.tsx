/** Shown while an admin page loads. */
export default function AdminLoading() {
  return (
    <div aria-busy="true" aria-label="Loading" className="space-y-6">
      <div className="h-9 w-64 animate-pulse bg-border/60" />
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {[0, 1, 2, 3].map((n) => <div key={n} className="h-24 animate-pulse bg-border/40" />)}
      </div>
      <div className="h-64 animate-pulse bg-border/40" />
    </div>
  );
}
