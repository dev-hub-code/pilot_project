/** The landing page's visual language: frosted glass panels, pill badges and two-tone headings. */

/** A frosted glass surface: translucent fill, hairline light border, soft shadow. Add rounding and padding. */
export const glass =
  "border border-glass-border bg-glass shadow-[0_18px_40px_-24px_rgb(11_42_85/0.35)] backdrop-blur-xl";

/**
 * A glass card that responds to the pointer: it lifts, firms up and picks up a soft accent glow. Pair with `glass`
 * and `group` so children (like IconTile) can react too.
 */
export const glassHover =
  "transition duration-300 ease-out hover:-translate-y-1 hover:border-gold/30 hover:bg-glass-strong hover:shadow-[0_28px_60px_-28px_rgb(31_98_201/0.45)]";

/** A small glass pill with a dot, above headings: "Monthly returns from a leasing business". */
export function Pill({ children, tone = "ink" }: { children: React.ReactNode; tone?: "ink" | "light" }) {
  return (
    <p className={`inline-flex items-center gap-2 rounded-full px-3.5 py-1.5 text-xs font-medium tracking-wide ${tone === "light"
      ? "border border-on-ink/20 bg-on-ink/10 text-on-ink backdrop-blur-md"
      : `${glass} text-foreground`}`}>
      <span aria-hidden="true" className={`size-1.5 rounded-full ${tone === "light" ? "bg-gold-on-ink" : "bg-ink dark:bg-gold"}`} />
      {children}
    </p>
  );
}

/**
 * A section heading in two tones: `lead` in ink, `soft` in a lighter grey, like "Choose Your" + "Tenure".
 * `tone="light"` for dark panels.
 */
export function Heading({ lead, soft, id, as: Tag = "h2", center = false, tone = "ink", className = "" }: {
  lead: string;
  soft?: string;
  id?: string;
  as?: "h1" | "h2" | "h3" | "p";
  center?: boolean;
  tone?: "ink" | "light";
  className?: string;
}) {
  return (
    <Tag id={id} className={`font-semibold tracking-tight text-balance ${center ? "mx-auto text-center" : ""} ${
      tone === "light" ? "text-on-ink" : "text-foreground"} ${className || "text-3xl leading-tight sm:text-4xl lg:text-[2.75rem]"}`}>
      {lead}
      {soft && <> <span className={tone === "light" ? "text-on-ink/60" : "text-heading-soft"}>{soft}</span></>}
    </Tag>
  );
}

/** A rounded square holding an icon, as in feature lists. */
export function IconTile({ children, tone = "ink" }: { children: React.ReactNode; tone?: "ink" | "light" }) {
  return (
    <span aria-hidden="true" className={`flex size-10 shrink-0 items-center justify-center rounded-xl ${tone === "light"
      ? "border border-on-ink/15 bg-on-ink/10 text-gold-on-ink"
      : `${glass} text-gold-text transition duration-300 group-hover:border-gold group-hover:bg-gold group-hover:text-on-gold`}`}>
      {children}
    </span>
  );
}
