import { PortPhoto, type PortPhotoName } from "@/components/brand/port-photo";
import { Section, SectionHeader, glass } from "./landing-ui";

/**
 * Indian ports, photographed. These photos are CC BY-SA / GODL-India (Wikimedia Commons), credited on the /credits
 * page: their licences require it.
 */
const PORTS: { photo: PortPhotoName; alt: string; caption: string }[] = [
  {
    photo: "jnpt-cfs", caption: "Nhava Sheva (JNPT), Navi Mumbai",
    alt: "Containers stacked high under a gantry crane at a container freight station at JNPT, Navi Mumbai",
  },
  { photo: "chennai-cfs", caption: "Chennai", alt: "Rows of shipping containers stacked several high in a container yard in Chennai" },
  {
    photo: "kolkata-port", caption: "Kolkata Port",
    alt: "Aerial view of container stacks and a container ship at Netaji Subhas Dock, Kolkata Port",
  },
  {
    photo: "tuticorin-terminal", caption: "V.O. Chidambaranar Port, Tuticorin",
    alt: "Stacked containers on the quay beside a container ship under cranes at the Dakshin Bharat Gateway Terminal, Tuticorin",
  },
  {
    photo: "kochi-terminal", caption: "Vallarpadam terminal, Kochi",
    alt: "A container ship loaded with containers at the quay of the International Container Transshipment Terminal, Kochi",
  },
];

/**
 * Some of the world's largest container lines, for reference only: SeaLease is not affiliated with them, so no copy
 * may present them as partners. Logos are the public-domain files on Wikimedia Commons, loaded from Wikimedia's CDN.
 * `small`: a solid or very bold logo, drawn smaller. `crop`: a file with wide margins, cut to this aspect ratio.
 */
const COMMONS_FILES = "https://upload.wikimedia.org/wikipedia/commons";
const LINES: { name: string; logo: string; small?: boolean; crop?: string }[] = [
  { name: "MSC", logo: "b/b3/Mediterranean_Shipping_Company_logo.svg" },
  { name: "Maersk", logo: "e/e6/Maersk_Group_Logo.svg" },
  { name: "CMA CGM", logo: "d/d1/CMA_CGM_logo.svg" },
  { name: "COSCO Shipping", logo: "f/f6/COSCOCS-ver1.svg" },
  { name: "Hapag-Lloyd", logo: "6/6a/Hapag-Lloyd.svg" },
  { name: "Ocean Network Express", logo: "4/45/Ocean_Network_Express_logo.svg" },
  { name: "Evergreen Line", logo: "c/c9/Evergreen_Line_Logo.svg" },
  { name: "HMM", logo: "5/56/HMM_Logo_Basic_Form.svg", small: true },
  { name: "ZIM", logo: "6/69/ZIM_Logo.svg" },
  { name: "PIL", logo: "3/30/PIL_logo.png", small: true, crop: "aspect-[360/105]" },
];

/** Where containers work: India's ports, and the lines that carry the trade. */
export function GlobalNetwork() {
  return (
    <Section id="network" labelledBy="network-heading">
      <SectionHeader id="network-heading" pill="Global Logistics Network" lead="Where The" soft="Containers Work" split>
        Containers are loaded and moved through India&apos;s terminals every day, from Nhava Sheva to Kochi. Most of
        that trade sails with a handful of global lines, and the steady demand for containers is what keeps leasing
        income coming in.
      </SectionHeader>

      <ul className="mt-12 grid grid-cols-2 gap-3 md:grid-cols-4 md:grid-rows-2 lg:gap-4">
        {PORTS.map((port, index) => (
          <li key={port.photo}
            className={`group relative overflow-hidden rounded-3xl bg-ink shadow-lg shadow-ink/15 ${index === 0
              ? "col-span-2 aspect-[4/3] md:row-span-2 md:aspect-auto"
              : "aspect-square md:aspect-[4/3]"}`}>
            <figure className="size-full">
              <PortPhoto name={port.photo} alt={port.alt}
                sizes={index === 0 ? "(min-width: 768px) 50vw, 100vw" : "(min-width: 768px) 25vw, 50vw"}
                className="size-full object-cover transition-transform duration-700 ease-out group-hover:scale-105 motion-reduce:transition-none" />
              <div aria-hidden="true" className="absolute inset-0 bg-gradient-to-t from-ink/65 via-transparent to-transparent" />
              <figcaption className="absolute bottom-3 left-3 rounded-full border border-on-ink/20 bg-ink/50 px-3 py-1.5 text-xs text-on-ink backdrop-blur-md sm:text-sm">
                {port.caption}
              </figcaption>
            </figure>
          </li>
        ))}
      </ul>

      <div className={`mt-5 rounded-3xl p-6 sm:p-8 ${glass}`}>
        <h3 className="text-sm font-semibold tracking-wide text-muted uppercase">Lines that carry the world&apos;s containers</h3>
        <ul className="mt-5 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
          {LINES.map((line) => (
            // Always a white tile: the logos are drawn for a light background, in dark mode too.
            <li key={line.name} className="flex h-20 items-center justify-center rounded-2xl bg-white px-5 shadow-sm sm:h-24 sm:px-7">
              {/* eslint-disable-next-line @next/next/no-img-element -- next/image adds inline styles blocked by our CSP */}
              <img src={`${COMMONS_FILES}/${line.logo}`} alt={line.name} title={line.name} loading="lazy" decoding="async"
                className={`w-auto max-w-full object-contain opacity-70 grayscale transition duration-300 hover:opacity-100 hover:grayscale-0 ${line.crop ? `${line.crop} h-7 object-cover sm:h-8` : line.small ? "max-h-7 sm:max-h-8" : "max-h-9 sm:max-h-11"}`} />
            </li>
          ))}
        </ul>
      </div>
    </Section>
  );
}
