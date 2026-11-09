/**
 * Photos served straight from Wikimedia Commons' CDN (upload.wikimedia.org, allowed by the CSP's img-src), at
 * the CDN's standard thumbnail widths. A plain <img> with srcset: next/image adds inline styles that our CSP blocks.
 * Photos under CC BY / CC BY-SA must also be listed in COMMONS_CREDITS, which the /credits page shows.
 */

/** Wikimedia's standard thumbnail steps: cached at the CDN, so they load fast. */
const WIDTHS = [330, 500, 960] as const;
const CDN = "https://upload.wikimedia.org/wikipedia/commons/thumb";

export interface CommonsFile {
  /** The file's place on the CDN: "<hash>/<hash2>/<encoded file name>", as in its upload.wikimedia.org URL. */
  path: string;
  title: string;
  author: string;
  licence: string;
  licenceUrl: string;
}

const COMMONS_PAGE = "https://commons.wikimedia.org/wiki/File:";
const CC_BY_SA_3 = { licence: "CC BY-SA 3.0", licenceUrl: "https://creativecommons.org/licenses/by-sa/3.0/" };
const CC_BY_SA_4 = { licence: "CC BY-SA 4.0", licenceUrl: "https://creativecommons.org/licenses/by-sa/4.0/" };
const CC0 = { licence: "CC0", licenceUrl: "https://creativecommons.org/publicdomain/zero/1.0/" };

/** The container photos on the home page's Container Types section. */
export const CONTAINER_PHOTOS = {
  dry20: {
    path: "9/96/20_ft_Dry_Container_%28DV%29_-_RAL_5010.jpg",
    title: "20 ft Dry Container (DV) - RAL 5010", author: "Hubert.Knop", ...CC_BY_SA_3,
  },
  dry40: {
    path: "a/ab/Triton_42G1_TRIU_542774_4.jpg",
    title: "Triton 42G1 TRIU 542774 4", author: "Col André Kritzinger", ...CC_BY_SA_3,
  },
  highCube40: {
    path: "f/f4/CAAU_911830-3_CAI_40%27_high_cube_container_CAI_International%2C_San_Francisco.jpg",
    title: "CAAU 911830-3 CAI 40' high cube container, CAI International, San Francisco", author: "Sludge G", ...CC_BY_SA_4,
  },
  reefer: {
    path: "a/a3/Reefer_containers.webp",
    title: "Reefer containers", author: "Wikideas1", ...CC0,
  },
} satisfies Record<string, CommonsFile>;

/** The Commons photos whose licence requires a credit, for the /credits page. */
export const COMMONS_CREDITS: (CommonsFile & { source: string })[] = Object.values(CONTAINER_PHOTOS)
  .filter((file) => file.licence !== "CC0")
  .map((file) => ({ ...file, source: COMMONS_PAGE + file.path.split("/").pop() }));

/**
 * @param sizes how wide the photo is shown, so the browser picks the right file
 */
export function CommonsPhoto({ file, alt, sizes, className }: { file: CommonsFile; alt: string; sizes: string; className?: string }) {
  const name = file.path.split("/").pop();
  const src = (w: number) => `${CDN}/${file.path}/${w}px-${name}`;
  return (
    // eslint-disable-next-line @next/next/no-img-element -- next/image adds inline styles blocked by our CSP
    <img src={src(500)} srcSet={WIDTHS.map((w) => `${src(w)} ${w}w`).join(", ")} sizes={sizes} alt={alt}
      loading="lazy" decoding="async" className={className} />
  );
}
