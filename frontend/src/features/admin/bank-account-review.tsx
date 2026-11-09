import Link from "next/link";
import { Notice } from "@/components/ui/notice";
import { StatusBadge } from "@/components/ui/status-badge";
import { toneFor } from "@/components/ui/status-tones";
import { DetailTile } from "@/components/ui/detail-tile";
import { BANK_PROOF_LABEL } from "@/features/profile/bank-labels";
import type { AdminBankAccount } from "@/types/user";
import { formatDateTime, humanize } from "@/utils/format";
import { Building2, Clock, CreditCard, ExternalLink, FileText, Hash, Landmark, Repeat, TriangleAlert, UserRound } from "lucide-react";
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
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h3 className="flex items-center gap-2 font-semibold">
          <Landmark aria-hidden="true" className="size-4 text-gold-text" />
          {account.bankName ?? "Bank account"}
        </h3>
        <div className="flex flex-wrap items-center gap-2">
          {account.replacesAccountId && <StatusBadge tone="warning">Change of account</StatusBadge>}
          <StatusBadge tone={toneFor(account.status)}>{humanize(account.status)}</StatusBadge>
        </div>
      </div>

      <dl className="grid gap-3 text-sm sm:grid-cols-2">
        <DetailTile icon={UserRound} label="Account holder name">{account.accountHolderName}</DetailTile>
        <DetailTile icon={CreditCard} label="Account number">
          <span className="font-mono text-base font-bold tracking-wide break-all">{accountNumber ?? account.accountNumber}</span>
        </DetailTile>
        <DetailTile icon={Hash} label="IFSC"><span className="font-mono text-base font-bold tracking-wide">{ifsc.code ?? "—"}</span></DetailTile>
        <DetailTile icon={Building2} label="Bank name">
          {account.bankName ?? <span className="text-muted">Not found for this IFSC</span>}
        </DetailTile>
        <DetailTile icon={Clock} label="Added">{formatDateTime(account.createdAt)}</DetailTile>
        <DetailTile icon={FileText} label="Proof of account">
          {account.proofType ? (
            <a href={`/platform/bank-accounts/${account.id}/proof`} target="_blank" rel="noopener noreferrer"
              className="inline-flex items-center gap-1.5 text-gold-text hover:underline">
              Open {BANK_PROOF_LABEL[account.proofType].toLowerCase()} <ExternalLink aria-hidden="true" className="size-3.5" />
            </a>
          ) : (
            <span className="text-rose-600 dark:text-rose-400">None uploaded</span>
          )}
          <span className="block text-xs font-normal text-muted">{account.proofType ? "Each view is recorded" : "Reject so the investor can add it again with one"}</span>
        </DetailTile>
        {replaces && (
          <DetailTile icon={Repeat} label="Replaces" className="sm:col-span-2">
            <span className="font-mono text-base font-bold tracking-wide">{replacesAccountNumber ?? replaces.accountNumberMasked}</span>
            <span className="block text-xs font-normal text-muted">
              {replaces.accountHolderName}, {humanize(replaces.status).toLowerCase()}{replaces.primary ? ", primary" : ""}. Verifying retires
              that account and moves payouts to this one; rejecting leaves it in use.
            </span>
          </DetailTile>
        )}
      </dl>

      {otherUsersWithSameAccount > 0 && (
        <p className="flex items-center gap-2 rounded-xl border border-rose-500/30 bg-rose-500/5 px-3 py-2 text-sm font-medium text-rose-700 dark:text-rose-400">
          <TriangleAlert aria-hidden="true" className="size-4 shrink-0" />
          Also registered by {otherUsersWithSameAccount} other {otherUsersWithSameAccount === 1 ? "user" : "users"}.
        </p>
      )}
      {!sharedAssignment && (
        <Link href={`/platform/users/${userId}?from=bank-verification`}
          className="inline-flex min-h-8 items-center gap-1.5 rounded-lg border border-border px-3 text-xs font-medium transition-colors hover:border-gold hover:text-gold-text">
          <UserRound aria-hidden="true" className="size-3.5" /> View account owner
        </Link>
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
