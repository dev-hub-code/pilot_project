/** Minimal accessible table shell; rows are rendered by the caller. */
export function DataTable({ columns, children }: { columns: readonly string[]; children: React.ReactNode }) {
  return (
    <div className="overflow-x-auto border border-border bg-surface">
      <table className="w-full text-left text-sm">
        <thead className="border-b border-border bg-background text-xs uppercase tracking-[0.08em] text-muted">
          <tr>
            {columns.map((column) => (
              <th key={column} scope="col" className="whitespace-nowrap px-4 py-3 font-medium">{column}</th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-border">{children}</tbody>
      </table>
    </div>
  );
}

export function Cell({ children, className = "" }: { children: React.ReactNode; className?: string }) {
  return <td className={`whitespace-nowrap px-4 py-3 ${className}`}>{children}</td>;
}
