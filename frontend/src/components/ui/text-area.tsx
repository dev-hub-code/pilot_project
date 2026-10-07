import type { TextareaHTMLAttributes } from "react";

interface TextAreaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  label: string;
  name: string;
  error?: string;
}

export function TextArea({ label, name, error, id, rows = 4, ...props }: TextAreaProps) {
  const areaId = id ?? name;
  return (
    <div className="space-y-1.5">
      <label htmlFor={areaId} className="block text-xs font-medium tracking-[0.08em] text-muted uppercase">{label}</label>
      <textarea
        id={areaId}
        name={name}
        rows={rows}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${areaId}-error` : undefined}
        className="w-full rounded-none border border-border bg-surface px-3 py-2 text-sm outline-none transition-colors focus:border-gold focus:ring-1 focus:ring-gold aria-invalid:border-rose-600"
        {...props}
      />
      {error && <p id={`${areaId}-error`} className="text-xs text-rose-600 dark:text-rose-400">{error}</p>}
    </div>
  );
}
