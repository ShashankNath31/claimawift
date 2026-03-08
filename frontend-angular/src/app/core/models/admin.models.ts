import { RoleName } from './auth.models';

export type UserStatus = 'ACTIVE' | 'INACTIVE' | 'SUSPENDED';

export interface AdminCreateUserRequest {
  username: string;
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  phoneNumber?: string;
  roles: RoleName[];
  status?: UserStatus;
}

export interface RoleUpdateRequest {
  roles: RoleName[];
}

export interface StatusUpdateRequest {
  status: UserStatus;
}
