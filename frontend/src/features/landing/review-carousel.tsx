import { Carousel } from "@/components/ui/carousel";

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
    <Carousel label="Investor reviews" itemsName="reviews" autoplay itemClassName="flex flex-col gap-6 p-8 text-left">
      {reviews.map((review, index) => (
        <div key={index} className="contents">
          <blockquote className="flex-1 text-base leading-relaxed">“{review.quote}”</blockquote>
          <p className="text-sm">
            <span className="font-semibold">{review.name}</span>
            <span className="block text-muted">{review.detail}</span>
          </p>
        </div>
      ))}
    </Carousel>
  );
}
