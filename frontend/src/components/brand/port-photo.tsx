/**
 * Port photos for the website, served as WebP from public/images/port at the widths generated for each.
 * A plain <img> with srcset: next/image adds inline styles that our CSP blocks.
 *
 * From Wikimedia Commons. These are public domain or CC0, so no credit line is required:
 *   savannah-cranes     File:Port of Savannah.jpeg
 *   terminal-above      File:Container terminal from above (Unsplash).jpg
 *   container-wall      File:Shipping container pattern (Unsplash).jpg
 *   yard-stacks         File:Shipping containers lie stacked in Mogadishu port in Somalia on September 17, 2015 (27447020401).jpg
 *   container-lift      File:Port of Savannah - 2021.jpg
 *   miami-port          File:Aerial photograph of the Port of Miami Container Port.jpg
 * These are CC BY / CC BY-SA and must be shown with their credit (the landing page's port gallery does):
 *   jnpt-cfs            File:Allcargo CFS, JNPT, Mumbai.jpg (Bopaliaq, CC BY-SA 4.0)
 *   kochi-cranes        File:Vallarpadam Container Terminal.JPG (Bharat S Raj, CC BY-SA 3.0)
 *   chennai-port        File:Chennai Port Container terminal.JPG (VtTN, CC BY 3.0), cropped to the cranes
 *   kochi-sunset        File:Sunset in vallarpadam container terminal kochi.jpg (Calvin joseph, CC BY-SA 4.0)
 *   kochi-night         File:Kochi Container Terminal - കൊച്ചി കണ്ടൈനർ ടെർമിനൽ.jpg (Shijan Kaakkara, CC BY-SA 4.0)
 */

const WIDTHS = {
  "savannah-cranes": [960, 1600, 2400],
  "terminal-above": [640, 1280, 1920],
  "container-wall": [640, 1280, 1920],
  "yard-stacks": [640, 1280, 1920],
  "container-lift": [640, 1280, 1920],
  "miami-port": [640, 1280, 1920],
  "jnpt-cfs": [640, 1280, 1920],
  "kochi-cranes": [640, 1280, 1920],
  "chennai-port": [640, 1280, 1920],
  "kochi-sunset": [640, 1280, 1920],
  "kochi-night": [640, 1280, 1920],
} as const;

export type PortPhotoName = keyof typeof WIDTHS;

/**
 * @param alt   empty for a purely decorative background
 * @param sizes how wide the photo is shown, so the browser picks the right file
 * @param eager for the hero: loaded at once, at high priority
 */
export function PortPhoto({ name, alt, sizes, className, eager = false }: {
  name: PortPhotoName;
  alt: string;
  sizes: string;
  className?: string;
  eager?: boolean;
}) {
  const widths = WIDTHS[name];
  const src = (w: number) => `/images/port/${name}-${w}.webp`;
  return (
    // eslint-disable-next-line @next/next/no-img-element -- next/image adds inline styles blocked by our CSP
    <img
      src={src(widths[1])}
      srcSet={widths.map((w) => `${src(w)} ${w}w`).join(", ")}
      sizes={sizes}
      alt={alt}
      loading={eager ? "eager" : "lazy"}
      fetchPriority={eager ? "high" : "auto"}
      decoding="async"
      className={className}
    />
  );
}
