import type { InputHTMLAttributes } from "react";

interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  name: string;
  error?: string;
}

export function TextField({ label, name, error, id, ...props }: TextFieldProps) {
  const inputId = id ?? name;
  const errorId = `${inputId}-error`;
  return (
    <div className="space-y-1.5">
      <label htmlFor={inputId} className="block text-xs font-medium uppercase tracking-[0.08em] text-muted">
        {label}
      </label>
      <input
        id={inputId}
        name={name}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errorId : undefined}
        className="h-11 w-full rounded-none border border-border bg-surface px-3 text-sm outline-none transition-colors focus:border-gold focus:ring-1 focus:ring-gold aria-invalid:border-rose-600 read-only:bg-background read-only:text-muted"
        {...props}
      />
      {error && (
        <p id={errorId} className="text-xs text-rose-600 dark:text-rose-400">
          {error}
        </p>
      )}
    </div>
  );
}
