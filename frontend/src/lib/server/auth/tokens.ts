import "server-only";
import { createRemoteJWKSet, decodeJwt, jwtVerify } from "jose";
import { serverEnv } from "../env";

/** Claims of a verified platform access token (see backend PlatformClaims). */
export interface AccessTokenClaims {
  userId: string;
  sessionId: string;
  roles: string[];
  permissions: string[];
  expiresAt: number;
}

let jwks: ReturnType<typeof createRemoteJWKSet> | undefined;

function keySet() {
  // Fetched from the backend once and cached by jose; re-fetched on unknown "kid" (key rotation).
  jwks ??= createRemoteJWKSet(new URL("/api/v1/auth/jwks", serverEnv().BACKEND_URL));
  return jwks;
}

/**
 * Verifies signature (RS256 only), issuer, audience and expiry. Used for routing decisions and
 * rendering; the backend independently re-verifies every API call and also checks revocation.
 */
export async function verifyAccessToken(token: string): Promise<AccessTokenClaims | null> {
  const { JWT_ISSUER, JWT_AUDIENCE } = serverEnv();
  try {
    const { payload } = await jwtVerify(token, keySet(), {
      algorithms: ["RS256"],
      issuer: JWT_ISSUER,
      audience: JWT_AUDIENCE,
      requiredClaims: ["sub", "sid", "exp", "jti"],
    });
    return {
      userId: String(payload.sub),
      sessionId: String(payload.sid),
      roles: stringArray(payload.roles),
      permissions: stringArray(payload.permissions),
      expiresAt: (payload.exp ?? 0) * 1000,
    };
  } catch {
    return null;
  }
}

/** Unverified expiry read, only used to decide whether to refresh proactively. */
export function expiresWithin(token: string, ms: number): boolean {
  try {
    const { exp } = decodeJwt(token);
    return exp === undefined || exp * 1000 - Date.now() < ms;
  } catch {
    return true;
  }
}

function stringArray(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((v): v is string => typeof v === "string") : [];
}
