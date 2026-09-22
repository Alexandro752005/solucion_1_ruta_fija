export const USER_ROLES = [
  'SUPER_ADMIN',
  'ADMIN',
  'CONDUCTOR',
] as const;

export type UserRole = (typeof USER_ROLES)[number];

export const CRM_ROLES: readonly UserRole[] = [
  'SUPER_ADMIN',
  'ADMIN',
];

export interface LoginRequest {
  readonly email: string;
  readonly password: string;
}

export interface SessionUser {
  readonly id: string;
  readonly fullName: string;
  readonly email?: string;
  readonly role: UserRole;
  readonly organizationId?: string | null;
  readonly organizationName?: string | null;
}

export interface LoginResponse {
  readonly accessToken: string;
  readonly expiresIn: number;
  readonly user: SessionUser;
}

export type SessionStatus = 'unknown' | 'authenticated' | 'anonymous';
