import { Children, Fragment, cloneElement, isValidElement, type ReactElement, type ReactNode } from "react";

/**
 * Minimal accessible table shell; rows are rendered by the caller. Below the `sm` breakpoint each row is shown as a
 * stacked card instead (see `.data-table` in globals.css), each value labelled with its column's heading.
 */
export function DataTable({ columns, children, className = "" }: { columns: readonly string[]; children: ReactNode; className?: string }) {
  return (
    <div className={`overflow-x-auto border border-border bg-surface ${className}`}>
      <table className="data-table w-full text-left text-sm">
        <thead className="border-b border-border bg-background text-xs uppercase tracking-[0.08em] text-muted">
          <tr>
            {columns.map((column) => (
              <th key={column} scope="col" className="whitespace-nowrap px-4 py-3 font-medium">{column}</th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-border">{labelRows(children, columns)}</tbody>
      </table>
    </div>
  );
}

export function Cell({ children, className = "", label }: { children: ReactNode; className?: string; label?: string }) {
  // The inner element is `display: contents` on wider screens (no box: the cell lays out as before) and one block on
  // phones, so a cell's mixed content stays together beside its label.
  return (
    <td data-label={label || undefined} className={`whitespace-nowrap px-4 py-3 ${className}`}>
      <div className="data-table-value">{children}</div>
    </td>
  );
}

/** Gives each row's Cells their column's heading, for the phone layout. Rows are `<tr>`s, possibly in fragments. */
function labelRows(nodes: ReactNode, columns: readonly string[]): ReactNode {
  return Children.map(nodes, (row) => {
    if (!isValidElement<{ children?: ReactNode }>(row)) return row;
    if (row.type === Fragment) return cloneElement(row, undefined, labelRows(row.props.children, columns));
    if (row.type !== "tr") return row;
    let column = 0;
    return cloneElement(row, undefined, Children.map(row.props.children, (cell) => {
      if (!isValidElement(cell)) return cell;
      const label = columns[column++];
      return cell.type === Cell ? cloneElement(cell as ReactElement<{ label?: string }>, { label }) : cell;
    }));
  });
}
