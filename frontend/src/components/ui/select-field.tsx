import type { SelectHTMLAttributes } from "react";

interface SelectFieldProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label: string;
  name: string;
  options: readonly { value: string; label: string }[];
  error?: string;
  placeholder?: string;
}

export function SelectField({ label, name, options, error, placeholder, id, ...props }: SelectFieldProps) {
  const selectId = id ?? name;
  return (
    <div className="space-y-1.5">
      <label htmlFor={selectId} className="block text-xs font-medium uppercase tracking-[0.08em] text-muted">
        {label}
      </label>
      <select
        id={selectId}
        name={name}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${selectId}-error` : undefined}
        className="h-11 w-full rounded-none border border-border bg-surface px-3 text-sm outline-none transition-colors focus:border-gold focus:ring-1 focus:ring-gold aria-invalid:border-rose-600"
        {...props}
      >
        {placeholder !== undefined && <option value="">{placeholder}</option>}
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
      {error && (
        <p id={`${selectId}-error`} className="text-xs text-rose-600 dark:text-rose-400">
          {error}
        </p>
      )}
    </div>
  );
}
