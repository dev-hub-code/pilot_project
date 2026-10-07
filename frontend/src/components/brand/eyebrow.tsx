/** Section label: gold dot + uppercase text, as used above section headings. */
export function Eyebrow({ children, tone = "ink" }: { children: React.ReactNode; tone?: "ink" | "light" }) {
  return (
    <p className={`inline-flex items-center gap-2.5 text-sm uppercase tracking-[0.12em] ${tone === "light" ? "text-on-ink" : "text-foreground"}`}>
      <span aria-hidden="true" className="size-2 rounded-full bg-gold" />
      {children}
    </p>
  );
}
