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
  readonly accessToken?: string | null;
  readonly tokenType?: string | null;
  readonly expiresIn?: number | null;
  readonly user?: SessionUser | null;
  readonly emailVerificationRequired?: boolean;
  readonly verification?: EmailOtpChallenge;
}

export interface EmailOtpChallenge {
  readonly verificationToken: string;
  readonly otpExpiresAt: string;
  readonly challengeExpiresAt: string;
  readonly emailSent: boolean;
}

export type SessionStatus = 'unknown' | 'authenticated' | 'anonymous';
