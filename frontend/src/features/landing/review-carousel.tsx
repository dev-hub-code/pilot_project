import { Quote } from "lucide-react";
import { Carousel } from "@/components/ui/carousel";
import { glass, glassHover } from "./landing-ui";

export interface Review {
  quote: string;
  name: string;
  detail: string;
  /** A layout placeholder, never shown in production. */
  sample?: boolean;
}

/** Investor reviews, advancing on their own. */
export function ReviewCarousel({ reviews }: { reviews: readonly Review[] }) {
  return (
    <Carousel label="Investor reviews" itemsName="reviews" autoplay variant="cards"
      itemClassName={`group flex flex-col gap-6 rounded-3xl p-8 text-left ${glass} ${glassHover}`}>
      {reviews.map((review, index) => (
        <div key={index} className="contents">
          <Quote aria-hidden="true" className="size-7 text-gold-text/70 transition duration-300 group-hover:scale-110 group-hover:text-gold-text" />
          <blockquote className="flex-1 text-base leading-relaxed">{review.quote}</blockquote>
          <p className="flex items-center gap-3 border-t border-border/70 pt-5 text-sm">
            <span aria-hidden="true" className="flex size-10 items-center justify-center rounded-full bg-ink font-display font-semibold text-on-ink transition-colors duration-300 group-hover:bg-gold group-hover:text-on-gold">
              {review.name.charAt(0)}
            </span>
            <span>
              <span className="block font-semibold">{review.name}</span>
              <span className="block text-muted">{review.detail}</span>
            </span>
          </p>
        </div>
      ))}
    </Carousel>
  );
}
