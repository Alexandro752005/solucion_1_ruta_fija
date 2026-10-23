export interface AdminActivationClaim {
  readonly activationId: string;
  readonly activationToken: string;
  readonly expiresAt: string;
  readonly status: string;
}

export interface AdminActivationStatus {
  readonly activationId: string;
  readonly adminUserId: string;
  readonly organizationId: string;
  readonly status: string;
  readonly passwordSet: boolean;
  readonly emailVerified: boolean;
  readonly termsAccepted: boolean;
  readonly expiresAt: string;
  readonly replacesAdmin?: boolean;
}

export interface AdminActivationOtp {
  readonly verificationToken: string;
  readonly otpExpiresAt: string;
  readonly challengeExpiresAt: string;
  readonly emailSent: boolean;
}

export interface AdminActivationTerms {
  readonly documentKey: string;
  readonly version: string;
  readonly content: string;
  readonly contentSha256: string;
  readonly synthetic: boolean;
  readonly accepted: boolean;
  readonly acceptedAt: string | null;
}

export interface AdminActivationAcceptance {
  readonly acceptanceId: string;
  readonly adminUserId: string;
  readonly organizationId: string;
  readonly documentVersion: string;
  readonly contentSha256: string;
  readonly synthetic: boolean;
  readonly acceptedAt: string;
}
