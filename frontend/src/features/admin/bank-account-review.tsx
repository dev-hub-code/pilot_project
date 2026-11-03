import Link from "next/link";
import { Notice } from "@/components/ui/notice";
import { StatusBadge } from "@/components/ui/status-badge";
import { toneFor } from "@/components/ui/status-tones";
import { BankAccountDetails } from "@/features/profile/bank-account-details";
import { BANK_PROOF_LABEL } from "@/features/profile/bank-labels";
import type { AdminBankAccount } from "@/types/user";
import { formatDateTime, humanize } from "@/utils/format";
import {
  assignBankAccountAction, rejectBankAccountAction, unassignBankAccountAction, verifyBankAccountAction,
} from "./actions";
import { ConfirmForm, ReasonForm } from "./decision-forms";
import { ReviewAssignment } from "./review-assignment";

/**
 * One investor bank account for a reviewer to check, with its decision while it awaits verification. `canVerify` is
 * false while the owner's identity is not approved: the account can be rejected but not yet verified.
 */
export function BankAccountReview({ item, viewerId, canVerify = true, sharedAssignment = false }: {
  item: AdminBankAccount;
  viewerId: string;
  canVerify?: boolean;
  /** The page has one Assign to me for everything on it, so this account has none of its own. */
  sharedAssignment?: boolean;
}) {
  const { userId, account, otherUsersWithSameAccount, replaces, assignee, ifsc, accountNumber, replacesAccountNumber } = item;
  const decision = (
    <div className="grid gap-4 sm:grid-cols-2">
      {canVerify && (
        <ConfirmForm action={verifyBankAccountAction.bind(null, account.id)} submitLabel="Verify"
          confirm={replaces
            ? `Verify ${accountNumber ?? account.accountNumberMasked}? It replaces ${replacesAccountNumber ?? replaces.accountNumberMasked}, which is retired.`
            : `Verify ${accountNumber ?? account.accountNumberMasked}?`} />
      )}
      <ReasonForm action={rejectBankAccountAction.bind(null, account.id)} label="Rejection reason"
        submitLabel="Reject" />
    </div>
  );
  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-start justify-between gap-3 text-sm">
        <div className="min-w-0 flex-1 space-y-3">
          <BankAccountDetails account={account} accountNumber={accountNumber} ifsc={ifsc.code} />
          <p className="text-muted">Added {formatDateTime(account.createdAt)}</p>
          <Link href={`/admin/users/${userId}?from=bank-verification`} className="text-brand hover:underline">View account owner</Link>
        </div>
        <div className="flex items-center gap-2">
          {account.replacesAccountId && <StatusBadge tone="warning">Change of account</StatusBadge>}
          <StatusBadge tone={toneFor(account.status)}>{humanize(account.status)}</StatusBadge>
        </div>
      </div>
      {replaces && (
        <p className="border-l-4 border-gold bg-background px-4 py-3 text-sm">
          Replaces{" "}
          <span className="font-mono text-base font-bold tracking-wide">{replacesAccountNumber ?? replaces.accountNumberMasked}</span> ({replaces.accountHolderName},{" "}
          {humanize(replaces.status).toLowerCase()}{replaces.primary ? ", primary" : ""}). Verifying retires that
          account and moves payouts to this one; rejecting leaves it in use.
        </p>
      )}
      <p className="text-sm">
        {account.proofType ? (
          <a href={`/admin/bank-accounts/${account.id}/proof`} target="_blank" rel="noopener noreferrer"
            className="text-brand hover:underline">
            Open {BANK_PROOF_LABEL[account.proofType].toLowerCase()} (each view is recorded)
          </a>
        ) : (
          <span className="font-medium text-rose-600 dark:text-rose-400">
            No bank statement, cancelled cheque or passbook: reject so the investor can add the account again with one.
          </span>
        )}
      </p>
      {otherUsersWithSameAccount > 0 && (
        <p className="text-sm font-medium text-rose-600 dark:text-rose-400">
          Warning: also registered by {otherUsersWithSameAccount} other user(s).
        </p>
      )}
      {userId !== viewerId && account.status === "PENDING_VERIFICATION" && (sharedAssignment ? (
        assignee?.id === viewerId ? decision : assignee ? (
          <Notice tone="info">Assigned to {assignee.name}. Only they can work on it.</Notice>
        ) : (
          <p className="text-sm text-muted">Use Assign to me on this page to work on it.</p>
        )
      ) : (
        <ReviewAssignment assignee={assignee} viewerId={viewerId}
          assign={assignBankAccountAction.bind(null, account.id)}
          unassign={unassignBankAccountAction.bind(null, account.id)}>
          {decision}
        </ReviewAssignment>
      ))}
    </div>
  );
}
