/**
 * Port photos for the website, served as WebP from public/images/port at the widths generated for each.
 * A plain <img> with srcset: next/image adds inline styles that our CSP blocks.
 *
 * All photos are public domain or CC0 (Wikimedia Commons), so no credit line is required:
 *   savannah-cranes     File:Port of Savannah.jpeg
 *   terminal-above      File:Container terminal from above (Unsplash).jpg
 *   container-wall      File:Shipping container pattern (Unsplash).jpg
 *   tacoma-cranes       File:Aerial view of shipping containers, and big container cranes at Tacoma's container port -a.jpg
 *   savannah-aerial     File:Port of Savannah 2.jpeg
 *   singapore-terminal  File:Shipping terminal in Singapore (Unsplash).jpg
 *   yard-stacks         File:Shipping containers lie stacked in Mogadishu port in Somalia on September 17, 2015 (27447020401).jpg
 *   container-lift      File:Port of Savannah - 2021.jpg
 *   miami-port          File:Aerial photograph of the Port of Miami Container Port.jpg
 */

const WIDTHS = {
  "savannah-cranes": [960, 1600, 2400],
  "terminal-above": [640, 1280, 1920],
  "container-wall": [640, 1280, 1920],
  "tacoma-cranes": [640, 1280, 1920],
  "savannah-aerial": [640, 1280, 1920],
  "singapore-terminal": [640, 1280, 1920],
  "yard-stacks": [640, 1280, 1920],
  "container-lift": [640, 1280, 1920],
  "miami-port": [640, 1280, 1920],
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
