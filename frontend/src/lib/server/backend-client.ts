import "server-only";
import type { ApiError } from "@/types/api";
import { serverEnv } from "./env";

/** Error raised for any non-2xx backend response, carrying the backend's ApiError when present. */
export class BackendError extends Error {
  constructor(
    readonly status: number,
    readonly apiError: ApiError | null,
    readonly correlationId: string,
  ) {
    super(apiError?.message ?? `Backend request failed with status ${status}`);
    this.name = "BackendError";
  }
}

export interface BackendRequestInit extends Omit<RequestInit, "body"> {
  /** JSON-serialisable body; sent with Content-Type: application/json. */
  json?: unknown;
  /** Multipart body (file uploads); the boundary header is set by fetch. */
  formData?: FormData;
  /** Propagated as X-Correlation-Id; generated when omitted. */
  correlationId?: string;
  /** Required by the backend for financial POSTs (orders, payments, withdrawals). */
  idempotencyKey?: string;
}

/**
 * Typed fetch against the Spring Boot API. Server-only: the browser never talks to the backend
 * directly. Responses are never cached - financial data must always be fresh.
 */
export async function backendFetch<T>(path: string, init: BackendRequestInit = {}): Promise<T> {
  const response = await backendRequest(path, init);
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

/** Like {@link backendFetch} but returns the raw response, e.g. for streaming files. */
export async function backendRequest(path: string, init: BackendRequestInit = {}): Promise<Response> {
  const { BACKEND_URL, BACKEND_TIMEOUT_MS } = serverEnv();
  const { json, formData, correlationId = crypto.randomUUID(), idempotencyKey, headers, ...rest } = init;

  const requestHeaders = new Headers(headers);
  if (!requestHeaders.has("Accept")) requestHeaders.set("Accept", "application/json");
  requestHeaders.set("X-Correlation-Id", correlationId);
  if (json !== undefined) requestHeaders.set("Content-Type", "application/json");
  if (idempotencyKey) requestHeaders.set("Idempotency-Key", idempotencyKey);

  const response = await fetch(new URL(path, BACKEND_URL), {
    ...rest,
    headers: requestHeaders,
    body: formData ?? (json === undefined ? undefined : JSON.stringify(json)),
    cache: "no-store",
    signal: rest.signal ?? AbortSignal.timeout(BACKEND_TIMEOUT_MS),
  });

  if (!response.ok) {
    const responseCorrelationId = response.headers.get("X-Correlation-Id") ?? correlationId;
    throw new BackendError(response.status, await readApiError(response), responseCorrelationId);
  }
  return response;
}

async function readApiError(response: Response): Promise<ApiError | null> {
  const contentType = response.headers.get("Content-Type") ?? "";
  if (!contentType.includes("application/json")) return null;
  try {
    return (await response.json()) as ApiError;
  } catch {
    return null;
  }
}
