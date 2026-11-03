import Link from "next/link";
import { Notice } from "@/components/ui/notice";
import type { Portfolio } from "@/types/order";

/** Asks the investor to accept the agreements of paid investments: their returns start only once accepted. */
export function AgreementPrompt({ holdings }: { holdings: Portfolio["holdings"] }) {
  const toAccept = holdings.filter((h) => h.status === "AWAITING_ACCEPTANCE");
  const first = toAccept[0];
  if (!first) {
    return null;
  }
  return (
    <Notice tone="warning">
      <strong>{toAccept.length === 1 ? "An investment agreement is" : `${toAccept.length} investment agreements are`} waiting for you.</strong>{" "}
      Your returns start once you accept the
      agreement: <Link href={`/portfolio/${first.id}/agreement`} className="font-medium underline">review and accept</Link>.
    </Notice>
  );
}
