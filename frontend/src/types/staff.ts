/** Mirrors of the backend's employee-account DTOs. */

/** Returned once when an account is created or its password reset; the password cannot be fetched again. */
export interface StaffCredentials {
  userId: string;
  email: string;
  firstName: string;
  lastName: string;
  roles: string[];
  temporaryPassword: string;
  expiresAt: string;
  /** Whether the credentials were also emailed to the employee. */
  emailed: boolean;
}

export interface UserAuthorities {
  roles: string[];
  permissions: string[];
}
