export interface AdminTermsDocument {
  readonly documentKey: string;
  readonly version: string;
  readonly content: string;
  readonly contentSha256: string;
  readonly synthetic: boolean;
  readonly accepted: boolean;
  readonly acceptedAt: string | null;
}

export interface AdminTermsAcceptance {
  readonly acceptanceId: string;
  readonly adminUserId: string;
  readonly organizationId: string;
  readonly documentVersion: string;
  readonly contentSha256: string;
  readonly synthetic: boolean;
  readonly acceptedAt: string;
}
