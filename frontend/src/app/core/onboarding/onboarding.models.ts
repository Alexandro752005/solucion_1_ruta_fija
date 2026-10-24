export interface ConductorOnboardingSummary {
  readonly requestId: string;
  readonly status: string;
  readonly reviewRounds: number;
  readonly submittedAt: string;
}

/** Personal data is available only through the restricted expediente endpoint. */
export interface ConductorOnboardingExpediente {
  readonly requestId: string;
  readonly status: string;
  readonly fullName: string;
  readonly email: string;
  readonly phone: string | null;
}

export interface ExistingDriverRegularizationCommand {
  readonly operationKey: string;
  readonly reason: string;
  readonly identityPreservedConfirmed: true;
}

export interface ExistingDriverRegularization {
  readonly requestId: string;
  readonly status: string;
  readonly existingUserId: string;
  readonly existingDriverId: string;
}

export type DocumentaryResult = 'PENDING' | 'CONFORME' | 'OBSERVADO' | 'NO_APLICA_JUSTIFICADO' | 'POR_CONTRASTAR';
export interface DocumentaryRequirement {
  readonly requirementId: string;
  readonly title: string;
  readonly mandatory: boolean;
  readonly fieldKeys: readonly string[];
}
export interface DocumentaryDocument {
  requirementId: string;
  fields: Record<string, string>;
  expiryMode: 'DATED' | 'NO_EXPIRY' | 'UNKNOWN';
  validUntil: string | null;
  applicability: 'CONFIRMED' | 'POR_CONTRASTAR';
  condition: string;
  sourceReference: string;
  result: DocumentaryResult;
  reviewReference: string;
  observation: string;
}
export interface DocumentaryHistory {
  readonly action: string;
  readonly occurredAt: string;
  readonly reason: string | null;
  readonly round: number;
}
export interface DocumentaryExpediente {
  readonly requestId: string;
  readonly status: string;
  readonly version: number;
  readonly reviewRounds: number;
  readonly modality: 'TAXI' | 'COLECTIVO' | null;
  readonly catalogId: string;
  readonly catalogVersion: string;
  readonly catalogHash: string;
  readonly catalog: readonly DocumentaryRequirement[];
  readonly documents: readonly DocumentaryDocument[];
  readonly history: readonly DocumentaryHistory[];
  readonly correctionDeadline: string | null;
  readonly overdue: boolean;
  readonly personalApproved: boolean;
  readonly operationalEnabled: boolean;
  readonly reapplyAfter: string | null;
  readonly provisionedUserId?: string | null;
  readonly provisionedDriverId?: string | null;
  readonly existingAccountRegularization?: boolean;
  readonly existingUserId?: string | null;
  readonly existingDriverId?: string | null;
}
export interface DocumentaryMutation {
  readonly expectedVersion: number;
  readonly operationKey: string;
  readonly reason?: string;
}
export interface DocumentarySave extends DocumentaryMutation {
  readonly modality: 'TAXI' | 'COLECTIVO';
  readonly documents: readonly DocumentaryDocument[];
}
