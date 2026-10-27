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
 * The rest are CC BY / CC BY-SA, listed in PHOTO_CREDITS: their licences require the credit, which the photo
 * credits page (/credits, linked from the landing page footer) shows. Add any new such photo there.
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
  "mundra-port": [640, 1280, 1920],
  "kochi-night": [640, 1280, 1920],
} as const;

export type PortPhotoName = keyof typeof WIDTHS;

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
