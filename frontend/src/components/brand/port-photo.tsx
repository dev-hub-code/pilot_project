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
 * The rest are CC BY / CC BY-SA, listed in PHOTO_CREDITS: their licences require the credit, which the photo
 * credits page (/credits) shows. Add any new such photo there.
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
  "jnpt-cfs": { path: "5/5e/Allcargo_CFS%2C_JNPT%2C_Mumbai.jpg", thumb: "Allcargo_CFS%2C_JNPT%2C_Mumbai.jpg" },
  "kochi-cranes": { path: "6/6a/Vallarpadam_Container_Terminal.JPG", thumb: "Vallarpadam_Container_Terminal.JPG" },
  "chennai-port": { path: "6/6d/Chennai_Port_Container_terminal.JPG", thumb: "Chennai_Port_Container_terminal.JPG" },
  "mundra-port": { path: "a/af/The_port_of_Mundra_in_Gujarat.jpg", thumb: "The_port_of_Mundra_in_Gujarat.jpg" },
  "kochi-night": { path: "c/c5/Kochi_Container_Terminal_-_%E0%B4%95%E0%B5%8A%E0%B4%9A%E0%B5%8D%E0%B4%9A%E0%B4%BF_%E0%B4%95%E0%B4%A3%E0%B5%8D%E0%B4%9F%E0%B5%88%E0%B4%A8%E0%B5%BC_%E0%B4%9F%E0%B5%86%E0%B5%BC%E0%B4%AE%E0%B4%BF%E0%B4%A8%E0%B5%BD.jpg", thumb: "Kochi_Container_Terminal_-_%E0%B4%95%E0%B5%8A%E0%B4%9A%E0%B5%8D%E0%B4%9A%E0%B4%BF_%E0%B4%95%E0%B4%A3%E0%B5%8D%E0%B4%9F%E0%B5%88%E0%B4%A8%E0%B5%BC_%E0%B4%9F%E0%B5%86%E0%B5%BC%E0%B4%AE%E0%B4%BF%E0%B4%A8%E0%B5%BD.jpg" },
} as const;

/** Commons serves only standard thumbnail widths; others are refused. The hero starts larger. */
const WIDTHS = [500, 960, 1280, 1920] as const;
const HERO_WIDTHS = [960, 1280, 1920] as const;

export type PortPhotoName = keyof typeof PHOTOS;

export type PhotoCredit = { photo: PortPhotoName; title: string; source: string; author: string; licence: string; licenceUrl: string; changes?: string };

const COMMONS = "https://commons.wikimedia.org/wiki/File:";

/** Photos whose licence requires a credit: each one's original on Wikimedia Commons, author and licence. */
export const PHOTO_CREDITS: PhotoCredit[] = [
  {
    photo: "jnpt-cfs", title: "Allcargo CFS, JNPT, Mumbai", source: COMMONS + "Allcargo_CFS,_JNPT,_Mumbai.jpg",
    author: "Bopaliaq", licence: "CC BY-SA 4.0", licenceUrl: "https://creativecommons.org/licenses/by-sa/4.0/",
  },
  {
    photo: "kochi-cranes", title: "Vallarpadam Container Terminal", source: COMMONS + "Vallarpadam_Container_Terminal.JPG",
    author: "Bharat S Raj", licence: "CC BY-SA 3.0", licenceUrl: "https://creativecommons.org/licenses/by-sa/3.0/",
  },
  {
    photo: "chennai-port", title: "Chennai Port Container terminal", source: COMMONS + "Chennai_Port_Container_terminal.JPG",
    author: "VtTN", licence: "CC BY 3.0", licenceUrl: "https://creativecommons.org/licenses/by/3.0/", changes: "Cropped",
  },
  {
    photo: "mundra-port", title: "The port of Mundra in Gujarat", source: COMMONS + "The_port_of_Mundra_in_Gujarat.jpg",
    author: "Felix Dance", licence: "CC BY 2.0", licenceUrl: "https://creativecommons.org/licenses/by/2.0/",
  },
  {
    photo: "kochi-night", title: "Kochi Container Terminal",
    source: COMMONS + encodeURIComponent("Kochi_Container_Terminal_-_കൊച്ചി_കണ്ടൈനർ_ടെർമിനൽ.jpg"),
    author: "Shijan Kaakkara", licence: "CC BY-SA 4.0", licenceUrl: "https://creativecommons.org/licenses/by-sa/4.0/",
  },
];

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
