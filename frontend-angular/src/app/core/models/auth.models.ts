export type RoleName = 'ROLE_ADMIN' | 'ROLE_POLICYHOLDER' | 'ROLE_ADJUSTER' | 'ROLE_MANAGER';

export interface UserProfile {
  id: number;
  username: string;
  email: string;
  firstName?: string;
  lastName?: string;
  phoneNumber?: string;
  roles: RoleName[];
  status: string;
}

export interface AuthPayload {
  usernameOrEmail: string;
  password: string;
}

export interface VerifyOtpPayload {
  challengeId: string;
  otp: string;
}

export interface RegisterPayload {
  username: string;
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  phoneNumber?: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserProfile;
}

export interface LoginChallengeResponse {
  challengeId: string;
  maskedEmail: string;
  expiresInSeconds: number;
}
