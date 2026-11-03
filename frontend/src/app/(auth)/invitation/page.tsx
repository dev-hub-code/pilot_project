import type { Metadata } from "next";
import { Alert } from "@/components/ui/alert";
import { LinkButton } from "@/components/ui/link-button";
import { AcceptInvitationForm } from "@/features/auth/accept-invitation-form";
import { lookupInvitation } from "@/features/auth/actions";
import { getPhoneCountries } from "@/lib/server/phone-countries";

export const metadata: Metadata = { title: "Open your account" };

/** The target of an investor invitation email (?token=): the client opens their own network investor account. */
export default async function InvitationPage({ searchParams }: PageProps<"/invitation">) {
  const params = await searchParams;
  const token = typeof params.token === "string" ? params.token.slice(0, 100) : "";
  const invitation = token ? await lookupInvitation(token)
    : { ok: false as const, message: "This page needs the link from your invitation email." };

  if (!invitation.ok) {
    return (
      <div className="space-y-6">
        <div className="space-y-2">
          <h1 className="text-4xl font-semibold tracking-tight">Invitation not valid</h1>
        </div>
        <Alert>{invitation.message}</Alert>
        <LinkButton href="/login" className="w-full">Sign in</LinkButton>
      </div>
    );
  }

  const countries = await getPhoneCountries();
  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <h1 className="text-4xl font-semibold tracking-tight">Open your account</h1>
        <p className="text-sm text-muted">
          {invitation.invitedBy} at SeaLease invited you to open a network investor account. Enter your details and choose a password.
        </p>
      </div>
      <AcceptInvitationForm token={token} email={invitation.email} firstName={invitation.firstName}
        lastName={invitation.lastName} countries={countries} />
    </div>
  );
}
