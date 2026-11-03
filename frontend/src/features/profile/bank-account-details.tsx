import type { BankAccount } from "@/types/user";

/** A bank account's details, each with its label: holder name, account number, IFSC and bank. */
export function BankAccountDetails({ account, accountNumber, ifsc }: {
  account: Pick<BankAccount, "accountHolderName" | "bankName" | "accountNumber">;
  /** Overrides the account's own, e.g. the reviewer's copy. */
  accountNumber?: string | null;
  ifsc?: string | null;
}) {
  const number = accountNumber ?? account.accountNumber;
  return (
    <dl className="grid gap-x-6 gap-y-3 text-sm sm:grid-cols-2">
      <Detail label="Account holder name">{account.accountHolderName}</Detail>
      <Detail label="Account number"><span className="font-mono text-base font-bold tracking-wide">{number}</span></Detail>
      <Detail label="IFSC"><span className="font-mono text-base font-bold tracking-wide">{ifsc ?? "—"}</span></Detail>
      <Detail label="Bank name">{account.bankName ?? <span className="text-muted">Not found for this IFSC</span>}</Detail>
    </dl>
  );
}

function Detail({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <dt className="text-muted">{label}</dt>
      <dd className="mt-0.5 font-medium">{children}</dd>
    </div>
  );
}
