import Link from "next/link";
import { CONTAINER_PHOTOS, CommonsPhoto, type CommonsFile } from "@/components/brand/commons-photo";
import { Section, SectionHeader, glass, glassHover } from "./landing-ui";

/**
 * The standard ISO container types that container leasing deals in, each with a photo from Wikimedia Commons' CDN. General industry facts with rounded, typical
 * figures: no fleet sizes, availability or prices, since the platform holds no container data. Investors buy units
 * of a plan, not specific containers, and the section says so.
 */
const TYPES: { name: string; code: string; photo: CommonsFile; alt: string; focus: string; specs: string[]; use: string }[] = [
  {
    name: "20 ft standard", code: "20' GP",
    photo: CONTAINER_PHOTOS.dry20, focus: "object-[center_45%]",
    alt: "Two blue 20-foot dry containers stacked on top of each other at a roadside",
    specs: ["About 33 m³ of space", "Carries up to about 28 tonnes"],
    use: "Heavy, dense cargo such as machinery, metals, tiles and grain.",
  },
  {
    name: "40 ft standard", code: "40' GP",
    photo: CONTAINER_PHOTOS.dry40, focus: "object-[center_55%]",
    alt: "A rust-red 40-foot standard container on a railway wagon",
    specs: ["About 67 m³ of space", "Carries up to about 26 tonnes"],
    use: "Larger loads such as furniture, textiles and general goods.",
  },
  {
    name: "40 ft high cube", code: "40' HC",
    photo: CONTAINER_PHOTOS.highCube40, focus: "object-[75%_30%]",
    alt: "A red 40-foot high cube container on a rail car, beside a yellow container",
    specs: ["About 76 m³ of space", "30 cm taller than standard"],
    use: "Light, bulky cargo such as cotton, plastics and packaged goods.",
  },
  {
    name: "Refrigerated (reefer)", code: "20' / 40' RF",
    photo: CONTAINER_PHOTOS.reefer, focus: "object-[center_60%]",
    alt: "White refrigerated containers with cooling units on a long freight train",
    specs: ["Its own cooling unit", "Typically −30 °C to +30 °C"],
    use: "Cargo kept at a set temperature: fruit, seafood and medicines.",
  },
];

export function ContainerCategories() {
  return (
    <Section id="containers" labelledBy="containers-heading">
      <SectionHeader id="containers-heading" pill="Container Types" lead="The Containers" soft="Behind The Business" split>
        Container leasing is built on a few standard types that carry most of the world&apos;s trade. Your investment
        is in a SeaLease plan, not in a particular container.
      </SectionHeader>
      <ul className="mt-12 grid gap-5 sm:grid-cols-2 xl:grid-cols-4">
        {TYPES.map((type) => (
          <li key={type.name} className={`group flex flex-col rounded-3xl p-6 ${glass} ${glassHover}`}>
            <div className="relative aspect-[4/3] overflow-hidden rounded-2xl bg-ink">
              <CommonsPhoto file={type.photo} alt={type.alt}
                sizes="(min-width: 1280px) 300px, (min-width: 640px) 45vw, 90vw"
                className={`size-full object-cover transition-transform duration-700 ease-out group-hover:scale-105 motion-reduce:transition-none ${type.focus}`} />
            </div>
            <div className="mt-5 flex items-baseline justify-between gap-3">
              <h3 className="text-lg font-semibold tracking-tight">{type.name}</h3>
              <span className="shrink-0 font-display text-xs font-semibold tracking-wider text-brass-text">{type.code}</span>
            </div>
            <ul className="mt-3 space-y-1.5 text-sm">
              {type.specs.map((spec) => (
                <li key={spec} className="flex items-center gap-2">
                  <span aria-hidden="true" className="size-1 rounded-full bg-brass" />
                  {spec}
                </li>
              ))}
            </ul>
            <p className="mt-4 border-t border-border pt-4 text-sm leading-relaxed text-muted">{type.use}</p>
          </li>
        ))}
      </ul>
      <p className="mt-6 text-xs text-muted">
        Sizes and loads are typical figures for each type; they vary by manufacturer. Photos from Wikimedia Commons,
        shown for illustration: see <Link href="/credits" className="underline underline-offset-2 hover:text-foreground">photo credits</Link>.
      </p>
    </Section>
  );
}
