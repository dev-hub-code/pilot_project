import "server-only";
import { BackendError, backendFetch } from "./backend-client";

export type ServiceState = "UP" | "DOWN" | "UNREACHABLE";

interface HealthResponse {
  status: string;
}

/** Readiness of the backend (includes its database connection). */
export async function getBackendStatus(): Promise<ServiceState> {
  try {
    const health = await backendFetch<HealthResponse>("/actuator/health/readiness");
    return health.status === "UP" ? "UP" : "DOWN";
  } catch (error) {
    if (error instanceof BackendError) return "DOWN";
    return "UNREACHABLE";
  }
}
