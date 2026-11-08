/**
 * Port photos for the website, served by the site itself as WebP from public/photos/{name}-{width}.webp. The files
 * are made from the Wikimedia Commons originals by scripts/build-photos.mjs: add a photo there too, and run it.
 * A plain <img> with srcset: next/image adds inline styles that our CSP blocks.
 *
 * Public domain or CC0, so no credit line is required:
 *   savannah-cranes     File:Port of Savannah.jpeg
 *   terminal-above      File:Container terminal from above (Unsplash).jpg
 *   container-wall      File:Shipping container pattern (Unsplash).jpg
 *   yard-stacks         File:Shipping containers lie stacked in Mogadishu port in Somalia on September 17, 2015 (27447020401).jpg
 * The rest are CC BY-SA or GODL-India, listed in PHOTO_CREDITS: their licences require the credit, which the photo
 * credits page (/credits) shows. Add any new such photo there.
 */

/** Must match SOURCES in scripts/build-photos.mjs. */
export type PortPhotoName =
  | "savannah-cranes" | "terminal-above" | "container-wall" | "yard-stacks"
  | "jnpt-cfs" | "chennai-cfs" | "kolkata-port" | "tuticorin-terminal" | "kochi-terminal";

/** Must match WIDTHS in scripts/build-photos.mjs. The hero starts larger. */
const WIDTHS = [500, 960, 1280, 1920] as const;
const HERO_WIDTHS = [960, 1280, 1920] as const;

export type PhotoCredit = { photo: PortPhotoName; title: string; source: string; author: string; licence: string; licenceUrl: string; changes?: string };

const COMMONS = "https://commons.wikimedia.org/wiki/File:";
const GODL = { licence: "GODL-India", licenceUrl: "https://data.gov.in/sites/default/files/Gazette_Notification_OGDL.pdf" };
const CC_BY_SA_4 = { licence: "CC BY-SA 4.0", licenceUrl: "https://creativecommons.org/licenses/by-sa/4.0/" };

/** Photos whose licence requires a credit: each one's original on Wikimedia Commons, author and licence. */
export const PHOTO_CREDITS: PhotoCredit[] = [
  { photo: "jnpt-cfs", title: "Allcargo CFS, JNPT, Mumbai", source: COMMONS + "Allcargo_CFS,_JNPT,_Mumbai.jpg", author: "Bopaliaq", ...CC_BY_SA_4 },
  { photo: "chennai-cfs", title: "Allcargo CFS, Chennai", source: COMMONS + "Allcargo_CFS,_Chennai.jpg", author: "Bopaliaq", ...CC_BY_SA_4 },
  {
    photo: "kolkata-port", title: "A container ship berthed at Berth No. 7 of Netaji Subhas Dock",
    source: COMMONS + "A_container_ship_barthed_at_Berth_No._7_of_Netaji_Subhas_Dock.png",
    author: "Syama Prasad Mookerjee Port, Kolkata", ...GODL,
  },
  {
    photo: "tuticorin-terminal", title: "Dakshin Bharat Gateway Terminal of V.O. Chidambaranar Port Authority",
    source: COMMONS + "Dakshin_Bharat_Gateway_Terminal_of_V.O._Chidambaranar_Port_Authority.jpg",
    author: "V.O. Chidambaranar Port Authority", ...GODL,
  },
  {
    photo: "kochi-terminal", title: "Mogral at the International Container Transshipment Terminal, Kochi",
    source: COMMONS + "Mogral_at_the_International_Container_Transshipment_Terminal,_Kochi.jpg",
    author: "ArulkumarRajamani", ...CC_BY_SA_4,
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
  const widths = eager ? HERO_WIDTHS : WIDTHS;
  const src = (w: number) => `/photos/${name}-${w}.webp`;
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
