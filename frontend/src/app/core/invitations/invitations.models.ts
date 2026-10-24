export interface CreateInvitationRequest {
  readonly recipientEmail: string;
}

/** Response returned once by the issue endpoint. The code is never part of a list response. */
export interface CreatedInvitation {
  readonly id: string;
  readonly recipientEmail: string;
  readonly code: string;
  readonly issuedAt: string;
  readonly expiresAt: string;
}

/** Safe invitation summary used by list and revoke endpoints. */
export interface InvitationSummary {
  readonly id: string;
  readonly recipientEmail: string;
  readonly issuedAt: string;
  readonly expiresAt: string;
  readonly revokedAt: string | null;
  readonly usedAt: string | null;
}

export interface InvitationPage {
  readonly items: readonly InvitationSummary[];
  readonly page: number;
  readonly size: number;
  readonly totalItems: number;
  readonly totalPages: number;
}
