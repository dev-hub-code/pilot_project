/**
 * Funding progress. Drawn with SVG geometry attributes rather than inline styles, because the
 * Content-Security-Policy blocks style attributes.
 */
export function ProgressBar({ percent, label }: { percent: number; label: string }) {
  const value = Math.min(100, Math.max(0, percent));
  return (
    <div role="progressbar" aria-label={label} aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(value)}>
      <svg viewBox="0 0 100 4" preserveAspectRatio="none" className="block h-1.5 w-full" aria-hidden="true">
        <rect width="100" height="4" className="fill-border" />
        <rect width={value} height="4" className="fill-gold" />
      </svg>
    </div>
  );
}
