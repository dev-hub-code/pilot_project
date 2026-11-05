/**
 * Port photos for the website, loaded from Wikimedia's CDN (upload.wikimedia.org): Commons' own resized copies of each
 * original, at its standard thumbnail widths. A plain <img> with srcset: next/image adds inline styles that our CSP
 * blocks.
 *
 * From Wikimedia Commons. These are public domain or CC0, so no credit line is required:
 *   savannah-cranes     File:Port of Savannah.jpeg
 *   terminal-above      File:Container terminal from above (Unsplash).jpg
 *   container-wall      File:Shipping container pattern (Unsplash).jpg
 *   yard-stacks         File:Shipping containers lie stacked in Mogadishu port in Somalia on September 17, 2015 (27447020401).jpg
 *   container-lift      File:Port of Savannah - 2021.jpg
 *   miami-port          File:Aerial photograph of the Port of Miami Container Port.jpg
 * A photo whose licence requires a credit (CC BY / CC BY-SA) also needs a credits page before it is added.
 */

/** Where Commons serves resized copies: {THUMBS}/{path}/{width}px-{thumb}. */
const THUMBS = "https://upload.wikimedia.org/wikipedia/commons/thumb";

/**
 * Each photo's file on Commons ({@code path}: hash directories and URL-encoded file name, as Commons gives them) and
 * the name its resized copies take ({@code thumb}: the file name, or "thumbnail.jpg" when the name is too long).
 */
const PHOTOS = {
  "savannah-cranes": { path: "b/b4/Port_of_Savannah.jpeg", thumb: "Port_of_Savannah.jpeg" },
  "terminal-above": { path: "a/aa/Container_terminal_from_above_%28Unsplash%29.jpg", thumb: "Container_terminal_from_above_%28Unsplash%29.jpg" },
  "container-wall": { path: "8/83/Shipping_container_pattern_%28Unsplash%29.jpg", thumb: "Shipping_container_pattern_%28Unsplash%29.jpg" },
  "yard-stacks": { path: "c/c9/Shipping_containers_lie_stacked_in_Mogadishu_port_in_Somalia_on_September_17%2C_2015._Mogadishu%27s_port%2C_having_recently_modernized_to_facilitate_the_shipping_of_containers%2C_has_made_it_much_easier_for_%2827447020401%29.jpg", thumb: "thumbnail.jpg" },
  "container-lift": { path: "1/14/Port_of_Savannah_-_2021.jpg", thumb: "Port_of_Savannah_-_2021.jpg" },
  "miami-port": { path: "5/52/Aerial_photograph_of_the_Port_of_Miami_Container_Port.jpg", thumb: "Aerial_photograph_of_the_Port_of_Miami_Container_Port.jpg" },
} as const;

/** Commons serves only standard thumbnail widths; others are refused. The hero starts larger. */
const WIDTHS = [500, 960, 1280, 1920] as const;
const HERO_WIDTHS = [960, 1280, 1920] as const;

export type PortPhotoName = keyof typeof PHOTOS;

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
  const { path, thumb } = PHOTOS[name];
  const widths = eager ? HERO_WIDTHS : WIDTHS;
  const src = (w: number) => `${THUMBS}/${path}/${w}px-${thumb}`;
  return (
    // eslint-disable-next-line @next/next/no-img-element -- next/image adds inline styles blocked by our CSP
    <img
      src={src(1280)}
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
