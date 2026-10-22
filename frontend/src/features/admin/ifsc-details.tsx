import type { AdminBankAccount } from "@/types/user";

/**
 * A bank account's IFSC for the reviewer: the branch the online directory of bank branches places it at when the
 * account was added, with a warning when that is another bank than the one typed, or when it could not be checked.
 */
export function IfscDetails({ ifsc, bankName, country }: { ifsc: AdminBankAccount["ifsc"]; bankName: string; country: string }) {
  return (
    <div className="space-y-1 text-sm">
      <p>
        <span className="text-muted">IFSC</span> <span className="font-mono">{ifsc.code}</span>
        {ifsc.bank ? (
          <span className="text-emerald-700 dark:text-emerald-400">
            {" "}· ✓ {ifsc.bank}{ifsc.branch ? `, ${ifsc.branch}` : ""}
          </span>
        ) : country === "IN" ? (
          <span className="text-amber-700 dark:text-amber-400"> · Not checked in the RBI branch list (it could not be reached): check it against the proof.</span>
        ) : null}
      </p>
      {!ifsc.bankMatches && (
        <p className="font-medium text-rose-600 dark:text-rose-400">
          Warning: the IFSC belongs to {ifsc.bank}, but the investor entered the bank as &ldquo;{bankName}&rdquo;.
        </p>
      )}
    </div>
  );
}
