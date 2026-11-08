import { Carousel } from "@/components/ui/carousel";
import type { PublicPlan } from "@/types/plan";
import { PlanCard, highestReturnCode } from "./plan-card";

/** The landing page's plans, as a carousel of plan cards. It never moves on its own: prices should hold still. */
export function PlanCarousel({ plans, href, action }: { plans: readonly PublicPlan[]; href: string; action: string }) {
  const top = highestReturnCode([...plans]);
  return (
    <Carousel label="Investment plans" itemsName="plans" variant="cards" itemClassName="flex">
      {plans.map((plan) => (
        <PlanCard key={plan.code} plan={plan} href={href} action={action} highlight={plan.code === top} />
      ))}
    </Carousel>
  );
}
