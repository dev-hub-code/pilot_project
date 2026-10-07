"use client";

import { type SelectHTMLAttributes, useEffect, useLayoutEffect, useRef } from "react";

interface SelectFieldProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label: string;
  name: string;
  options: readonly { value: string; label: string }[];
  error?: string;
  placeholder?: string;
}

export function SelectField({ label, name, options, error, placeholder, id, ...props }: SelectFieldProps) {
  const selectId = id ?? name;
  const ref = useRef<HTMLSelectElement>(null);
  // The value the select should show: the controlled value, else defaultValue.
  const intended = props.value ?? props.defaultValue;
  const latestIntended = useRef(intended);
  useLayoutEffect(() => {
    latestIntended.current = intended;
  });

  // React resets a form after its action runs, but a reset puts a <select> back on its first option
  // rather than on defaultValue, and React does not re-apply an unchanged controlled value. Restore
  // the intended value (which may itself have changed with the action's result) once the reset is done.
  useEffect(() => {
    const select = ref.current;
    const form = select?.form;
    if (!select || !form) return;
    const restore = () => setTimeout(() => {
      const wanted = latestIntended.current;
      if (wanted !== undefined) select.value = String(wanted ?? "");
    });
    form.addEventListener("reset", restore);
    return () => form.removeEventListener("reset", restore);
  }, []);

  return (
    <div className="space-y-1.5">
      <label htmlFor={selectId} className="block text-xs font-medium uppercase tracking-[0.08em] text-muted">
        {label}
      </label>
      <select
        ref={ref}
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
