"use client";

import { useEffect, useRef, useState, type ChangeEvent } from "react";

interface FileFieldProps {
  label: string;
  name: string;
  accept: string;
  id?: string;
  hint?: string;
  error?: string;
  required?: boolean;
  multiple?: boolean;
  onChange?: (event: ChangeEvent<HTMLInputElement>) => void;
}

interface Chosen {
  name: string;
  size: number;
}

function formatSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function UploadIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round"
      strokeLinejoin="round" aria-hidden="true" className="size-6 shrink-0">
      <path d="M12 15V4m0 0L7.5 8.5M12 4l4.5 4.5" />
      <path d="M4 15v3a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-3" />
    </svg>
  );
}

function FileIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round"
      strokeLinejoin="round" aria-hidden="true" className="size-5 shrink-0">
      <path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z" />
      <path d="M14 3v5h5" />
    </svg>
  );
}

/**
 * A file picker shown as an upload box: click it or drop files on it. The native input covers the box (invisible),
 * so choosing, dropping, keyboard focus and form submission all stay the browser's own.
 */
export function FileField({ label, name, accept, id, hint, error, required, multiple, onChange }: FileFieldProps) {
  const inputId = id ?? name;
  const input = useRef<HTMLInputElement>(null);
  const [chosen, setChosen] = useState<Chosen[]>([]);
  const [dragging, setDragging] = useState(false);

  // React resets the form after a submit, which empties the input without a change event.
  useEffect(() => {
    const form = input.current?.form;
    if (!form) return;
    const clear = () => setChosen([]);
    form.addEventListener("reset", clear);
    return () => form.removeEventListener("reset", clear);
  }, []);

  const tone = error
    ? "border-rose-600"
    : dragging
      ? "border-gold bg-gold/10"
      : "border-border hover:border-foreground/40";

  return (
    <div className="space-y-1.5">
      <label htmlFor={inputId} className="block text-sm font-medium text-foreground">
        {label}
        {!required && <span className="font-normal text-muted"> (optional)</span>}
      </label>
      <div className={`relative flex items-center gap-3 border border-dashed bg-surface px-4 py-4 transition-colors focus-within:border-gold focus-within:ring-1 focus-within:ring-gold ${tone}`}>
        <span className="text-gold-text"><UploadIcon /></span>
        <span className="min-w-0 flex-1 text-sm">
          <span className="font-medium text-foreground">
            {chosen.length > 0 ? (multiple ? "Choose other files" : "Choose another file") : multiple ? "Choose files" : "Choose a file"}
          </span>
          <span className="hidden text-muted sm:inline"> or drag {multiple ? "them" : "it"} here</span>
        </span>
        <span className="hidden border border-border px-3 py-1.5 text-xs font-medium text-foreground sm:inline-block">Browse</span>
        <input
          ref={input}
          id={inputId}
          name={name}
          type="file"
          accept={accept}
          multiple={multiple}
          aria-invalid={error ? true : undefined}
          aria-describedby={`${inputId}-hint`}
          className="absolute inset-0 size-full cursor-pointer opacity-0"
          onDragEnter={() => setDragging(true)}
          onDragLeave={() => setDragging(false)}
          onDrop={() => setDragging(false)}
          onChange={(event) => {
            setChosen(Array.from(event.target.files ?? []).map((f) => ({ name: f.name, size: f.size })));
            onChange?.(event);
          }}
        />
      </div>
      {chosen.length > 0 && (
        <ul className="space-y-1" aria-label="Chosen files">
          {chosen.map((file, i) => (
            <li key={`${file.name}-${i}`} className="flex items-center gap-2 border border-border bg-background px-3 py-2 text-sm">
              <span className="text-muted"><FileIcon /></span>
              <span className="min-w-0 flex-1 truncate text-foreground" title={file.name}>{file.name}</span>
              <span className="shrink-0 text-xs tabular-nums text-muted">{formatSize(file.size)}</span>
            </li>
          ))}
        </ul>
      )}
      {(error ?? hint) && (
        <p id={`${inputId}-hint`} className={`text-xs ${error ? "text-rose-600 dark:text-rose-400" : "text-muted"}`}>
          {error ?? hint}
        </p>
      )}
    </div>
  );
}
