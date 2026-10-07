/**
 * Mirrors of the backend's API contracts (com.sealease.backend.common.api).
 * Keep in sync with ApiError.java and PageResponse.java.
 */

export interface ApiFieldError {
  field: string;
  message: string;
}

export interface ApiError {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  path: string;
  correlationId?: string;
  fieldErrors?: ApiFieldError[];
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface PageRequest {
  page?: number;
  size?: number;
  /** e.g. "createdAt,desc" */
  sort?: string;
}
