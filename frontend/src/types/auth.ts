/** Mirrors backend CurrentUserResponse. */
export interface CurrentUser {
  id: string;
  /** The readable ID ("SL03500"). */
  userCode: string;
  email: string;
  firstName: string;
  lastName: string;
  status: string;
  roles: string[];
  permissions: string[];
  /** False until the user confirms their email address; they can sign in meanwhile. */
  emailVerified: boolean;
}

/** Mirrors backend RoleResponse. */
export interface Role {
  id: string;
  name: string;
  description: string;
  system: boolean;
  permissions: string[];
  version: number;
}
