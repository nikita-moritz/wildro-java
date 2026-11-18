"use client";

import { ElvixLeave, ElvixLifecycleWatcher, ElvixSessions, ElvixSignOutButton } from "@elvix.is/sdk/react";

/**
 * Ends the session here as well as at elvix: the SDK forgets its own copy, and
 * only this server can delete the httpOnly cookie. Then a full navigation, so
 * nothing rendered for the signed-in person is reused.
 */
async function endHere(target: string) {
  await fetch("/api/auth/logout", { method: "POST" }).catch(() => null);
  window.location.assign(target);
}

export function SignOutControl({ label, target }: { label: string; target: string }) {
  return (
    <ElvixSignOutButton
      label={label}
      tone="neutral"
      variant="outline"
      size="sm"
      showIcon={false}
      redirectAfterSignOut={null}
      onResult={() => void endHere(target)}
    />
  );
}

/** Every device the person is signed in on, one at a time or all at once (SPEC.md §18.1). */
export function SessionsControl({ target }: { target: string }) {
  return (
    <ElvixSessions
      signInUrl={null}
      onResult={(result) => {
        if (result.ok && result.action === "sign_out_all") {
          void endHere(target);
        }
      }}
    />
  );
}

/**
 * Leaving the application in elvix. elvix reports it to the user service, which
 * erases the name and the address and passes the rest on (SPEC.md §18.1).
 */
export function LeaveControl({ appName, target, privacyUrl, termsUrl }: { appName: string; target: string; privacyUrl: string; termsUrl: string }) {
  return (
    <ElvixLeave
      appName={appName}
      privacyPolicyUrl={privacyUrl}
      termsOfServiceUrl={termsUrl}
      onSuccess={(state) => {
        if (state === "left") {
          void endHere(target);
        }
      }}
    />
  );
}

/** Signs the person out within seconds when elvix blocks, pauses or deletes them. */
export function LifecycleControl({ target }: { target: string }) {
  return <ElvixLifecycleWatcher onSignedOut={() => void endHere(target)} />;
}
