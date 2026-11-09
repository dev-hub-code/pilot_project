import Link from "next/link";
import { ArrowRight } from "lucide-react";
import type { ReactNode } from "react";

/**
 * The landing page's building blocks: frosted glass panels, pill badges, two-tone headings, section frames, calls to
 * action, figures and feature cards. Every section is made from these, so spacing and type stay consistent.
 */

/** A frosted glass surface: translucent fill, hairline light border, soft shadow. Add rounding and padding. */
export const glass =
  "border border-glass-border bg-glass shadow-[0_18px_40px_-24px_rgb(11_42_85/0.35)] backdrop-blur-xl";

/** A glass card that lifts a little under the pointer. Pair with `glass` and `group`. */
export const glassHover =
  "transition duration-300 ease-out hover:-translate-y-1 hover:border-brass/40 hover:bg-glass-strong hover:shadow-[0_28px_60px_-28px_rgb(11_42_85/0.45)]";

/** The page's side gutters, shared by the header, every section and the footer. */
export const gutter = "px-4 sm:px-10 lg:px-14";

/** A small pill with a gold dot, above headings. */
export function Pill({ children, tone = "ink" }: { children: ReactNode; tone?: "ink" | "light" }) {
  return (
    <p className={`inline-flex items-center gap-2 rounded-full px-3.5 py-1.5 text-xs font-medium tracking-wide ${tone === "light"
      ? "border border-on-ink/20 bg-on-ink/10 text-on-ink backdrop-blur-md"
      : `${glass} text-foreground`}`}>
      <span aria-hidden="true" className={`size-1.5 rounded-full ${tone === "light" ? "bg-brass-on-ink" : "bg-brass"}`} />
      {children}
    </p>
  );
}

/**
 * A heading in two tones: `lead` in ink, `soft` in a lighter grey, like "Choose Your" + "Tenure".
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
export function IconTile({ children, tone = "ink" }: { children: ReactNode; tone?: "ink" | "light" }) {
  return (
    <span aria-hidden="true" className={`flex size-11 shrink-0 items-center justify-center rounded-xl ${tone === "light"
      ? "border border-on-ink/15 bg-on-ink/10 text-brass-on-ink"
      : `${glass} text-gold-text transition duration-300 group-hover:border-ink group-hover:bg-ink group-hover:text-brass-on-ink`}`}>
      {children}
    </span>
  );
}

/**
 * One page section: an anchor id (offset for the sticky header), the shared gutters and vertical rhythm.
 * Give it `labelledBy` when its heading has an id, so screen readers list it as a region.
 */
export function Section({ id, labelledBy, className = "", children }: {
  id?: string;
  labelledBy?: string;
  className?: string;
  children: ReactNode;
}) {
  return (
    <section id={id} aria-labelledby={labelledBy} className={`scroll-mt-24 py-10 sm:py-12 lg:py-14 ${gutter} ${className}`}>
      {children}
    </section>
  );
}

/**
 * A section's opening: pill, two-tone heading and a short introduction. Side by side on wide screens
 * (`split`), stacked and centred otherwise when `center` is set.
 */
export function SectionHeader({ id, pill, lead, soft, children, split = false, center = false, action }: {
  /** The heading's id, for the section's aria-labelledby. */
  id: string;
  pill: string;
  lead: string;
  soft?: string;
  /** The introduction. */
  children?: ReactNode;
  split?: boolean;
  center?: boolean;
  /** One call to action shown with the header. */
  action?: ReactNode;
}) {
  if (split) {
    return (
      <div className="grid gap-6 lg:grid-cols-2 lg:items-end lg:gap-14">
        <div className="space-y-5">
          <Pill>{pill}</Pill>
          <Heading id={id} lead={lead} soft={soft} />
        </div>
        <div className="space-y-5 lg:justify-self-end">
          {children && <div className="max-w-xl text-sm leading-relaxed text-muted sm:text-base">{children}</div>}
          {action}
        </div>
      </div>
    );
  }
  return (
    <div className={`space-y-5 ${center ? "text-center" : ""}`}>
      <Pill>{pill}</Pill>
      <Heading id={id} lead={lead} soft={soft} center={center} />
      {children && <div className={`max-w-2xl text-sm leading-relaxed text-muted sm:text-base ${center ? "mx-auto" : ""}`}>{children}</div>}
      {action}
    </div>
  );
}

const CTA_BASE =
  "group inline-flex min-h-12 items-center justify-center gap-2 rounded-xl px-6 text-sm font-semibold transition duration-300 focus-visible:ring-2 focus-visible:ring-brass focus-visible:ring-offset-2 focus-visible:outline-none";

const CTA_TONES = {
  /** The section's main action: solid ink with a gold edge on hover. */
  primary: "bg-ink text-on-ink shadow-lg shadow-ink/25 hover:-translate-y-0.5 hover:shadow-xl hover:shadow-ink/30 hover:ring-1 hover:ring-brass dark:bg-on-ink dark:text-ink",
  /** A quieter action beside a primary one. */
  secondary: `${glass} text-foreground hover:-translate-y-0.5 hover:border-ink/40 hover:bg-glass-strong`,
  /** On dark panels. */
  light: "bg-on-ink text-ink shadow-lg hover:-translate-y-0.5 hover:bg-brass-on-ink",
} as const;

/** A call to action. In-page targets ("#plans") use a plain anchor: <Link> skips the scroll when the hash is unchanged. */
export function CtaLink({ href, children, tone = "primary", arrow = true, className = "" }: {
  href: string;
  children: ReactNode;
  tone?: keyof typeof CTA_TONES;
  arrow?: boolean;
  className?: string;
}) {
  const content = (
    <>
      {children}
      {arrow && <ArrowRight aria-hidden="true" className="size-4 transition-transform duration-300 group-hover:translate-x-1" />}
    </>
  );
  const classes = `${CTA_BASE} ${CTA_TONES[tone]} ${className}`;
  return href.startsWith("#")
    ? <a href={href} className={classes}>{content}</a>
    : <Link href={href} className={classes}>{content}</Link>;
}

/**
 * A figure with its label, for dark panels: "₹12 Cr+" / "Returns credited to investors". Use inside a <dl> in an
 * `@container`: where the container is narrow the figure and label sit side by side in a full-width row, and from
 * 32rem up (room for three across) the figure stacks above its label.
 */
export function Stat({ label, value, note }: { label: string; value: string; note?: string }) {
  return (
    // The figure shows above its label, while <dt> stays first for screen readers.
    <div className="flex items-center justify-between gap-4 rounded-2xl border border-on-ink/10 bg-on-ink/[0.06] px-5 py-4 @lg:flex-col @lg:items-start @lg:justify-start @lg:gap-2 @lg:px-4 @lg:py-6 @2xl:px-6">
      <dt className="order-2 text-right text-sm leading-snug text-on-ink/75 @lg:text-left">{label}</dt>
      <dd className="order-1 font-display text-3xl font-semibold whitespace-nowrap text-brass-on-ink tabular-nums @2xl:text-4xl">{value}</dd>
      {note && <dd className="order-3 text-xs leading-snug text-on-ink/55">{note}</dd>}
    </div>
  );
}

/** A card with an icon, title and a short body. Renders as an <li>: place inside a list. */
export function FeatureCard({ icon, title, children }: { icon: ReactNode; title: string; children: ReactNode }) {
  return (
    <li className={`group space-y-4 rounded-3xl p-7 ${glass} ${glassHover}`}>
      <IconTile>{icon}</IconTile>
      <h3 className="text-lg font-semibold tracking-tight">{title}</h3>
      <div className="text-sm leading-relaxed text-muted">{children}</div>
    </li>
  );
}
