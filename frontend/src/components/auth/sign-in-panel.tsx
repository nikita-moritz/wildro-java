"use client";

import { ElvixSignInForm, getElvixToken } from "@elvix.is/sdk/react";
import { useRef, useState } from "react";

/**
 * elvix's sign-in card. On success the token goes to /api/auth/session, which
 * keeps it in an httpOnly cookie, and the browser then loads the account page
 * as a full navigation: a soft one could reuse a render made before the cookie
 * existed (paid for once on sia, niki/knowledge/elvix.md).
 */
export function SignInPanel({
  next,
  privacyUrl,
  termsUrl,
  failedText,
}: {
  next: string;
  privacyUrl: string;
  termsUrl: string;
  failedText: string;
}) {
  const handedOver = useRef(false);
  const [failed, setFailed] = useState(false);

  async function handOver(token: string | undefined) {
    // After a Google round trip both callbacks can fire for the same sign-in.
    if (handedOver.current) {
      return;
    }
    handedOver.current = true;
    const response = await fetch("/api/auth/session", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ token: token ?? getElvixToken() }),
    }).catch(() => null);
    if (response?.ok) {
      window.location.assign(next);
    } else {
      handedOver.current = false;
      setFailed(true);
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <ElvixSignInForm
        brandColor="#0d874a"
        onBrandColor="#ffffff"
        privacyPolicyUrl={privacyUrl}
        termsOfServiceUrl={termsUrl}
        onAuthenticated={(result) => void handOver(result.token)}
      />
      {failed && (
        <p role="alert" className="text-center text-sm text-destructive">
          {failedText}
        </p>
      )}
    </div>
  );
}
