"use client";

import { useActionState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import type { Money } from "@/types/plan";
import { formatMoney } from "@/utils/money";
import type { FormState } from "@/validators/form-state";
import { payFromBalanceAction } from "./actions";

/**
 * Online investors: pay the order from their available balance (returns first, then referral commission), confirmed
 * with their password. Shown only when the balance covers the order; the backend checks it again before spending.
 */
export function BalancePaymentForm({ orderId, orderNumber, total, available, idempotencyKey }: {
  orderId: string; orderNumber: string; total: Money; available: Money; idempotencyKey: string;
}) {
  const [state, action] = useActionState<FormState, FormData>(payFromBalanceAction.bind(null, orderId, idempotencyKey), {});
  const left = (Number(available.amount) - Number(total.amount)).toFixed(2);

  return (
    <form action={action} className="space-y-4 text-sm" noValidate>
      <div className="space-y-1">
        <p className="font-medium">Pay from your available balance</p>
        <p className="text-xs text-muted">
          {formatMoney(available)} available · {formatMoney({ amount: left, currency: available.currency })} left after this order.
          Confirmed at once; your returns are spent first, then your referral commission.
        </p>
      </div>
      <FormFeedback state={state} />
      <TextField id={`balance-password-${orderId}`} label="Confirm with your password" name="password" type="password"
        autoComplete="current-password" required error={state.fieldErrors?.password} />
      <SubmitButton className="w-full" pendingLabel="Paying…"
        confirm={`Pay ${formatMoney(total)} for ${orderNumber} from your available balance?`}>
        Pay {formatMoney(total)} from balance
      </SubmitButton>
    </form>
  );
}
