interface FileFieldProps {
  label: string;
  name: string;
  accept: string;
  hint?: string;
  error?: string;
  required?: boolean;
}

export function FileField({ label, name, accept, hint, error, required }: FileFieldProps) {
  return (
    <div className="space-y-1.5">
      <label htmlFor={name} className="block text-xs font-medium uppercase tracking-[0.08em] text-muted">
        {label}
        {!required && <span className="font-normal text-muted"> (optional)</span>}
      </label>
      <input
        id={name}
        name={name}
        type="file"
        accept={accept}
        aria-invalid={error ? true : undefined}
        aria-describedby={`${name}-hint`}
        className="block w-full text-sm file:mr-3 file:rounded-none file:border-0 file:bg-ink file:text-on-ink file:px-3 file:py-1.5 file:text-sm"
      />
      <p id={`${name}-hint`} className={`text-xs ${error ? "text-rose-600 dark:text-rose-400" : "text-muted"}`}>
        {error ?? hint}
      </p>
    </div>
  );
}
