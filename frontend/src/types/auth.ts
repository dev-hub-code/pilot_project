/** Mirrors backend CurrentUserResponse. */
export interface CurrentUser {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  status: string;
  roles: string[];
  permissions: string[];
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
