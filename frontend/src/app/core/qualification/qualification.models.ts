import { DocumentaryDocument } from '../onboarding/onboarding.models';

export type QualificationScope = 'VEHICLE' | 'COMPANY';
export type UnitKind = 'OWN' | 'COMPANY';
export interface QualificationRequirement {
  readonly scope: QualificationScope;
  readonly id: string;
  readonly title: string;
  readonly mandatory: boolean;
  readonly fieldKeys: readonly string[];
}
export interface QualificationReview {
  scope: QualificationScope;
  requirementId: string;
  vehicleId: string | null;
  fields: Record<string, string>;
  expiryMode: 'DATED' | 'NO_EXPIRY' | 'UNKNOWN';
  validUntil: string | null;
  applicability: 'CONFIRMED' | 'POR_CONTRASTAR';
  condition: string | null;
  sourceReference: string | null;
  result: 'CONFORME' | 'OBSERVADO' | 'NO_APLICA_JUSTIFICADO' | 'POR_CONTRASTAR';
  reviewReference: string | null;
  version?: number;
}
export interface QualificationPeriod {
  readonly id: string;
  readonly vehicleId: string;
  readonly kind: UnitKind;
  readonly startsAt: string;
  readonly endsAt: string | null;
  readonly status: 'AUTHORIZED' | 'REVOKED';
  readonly reason: string;
  readonly version: number;
}
export interface QualificationUnitRequest {
  readonly id: string;
  readonly vehicleId: string | null;
  readonly kind: UnitKind;
  readonly status: 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED';
  readonly reference: string;
  readonly createdAt: string;
}
export interface Qualification {
  readonly driverId: string;
  readonly accountId: string;
  readonly requestId: string;
  readonly version: number;
  readonly suspended: boolean;
  readonly operationalEnabled: boolean;
  readonly personalDocumentsStatus: string;
  readonly personalValidUntil: string | null;
  readonly blockers: readonly string[];
  readonly periods: readonly QualificationPeriod[];
  readonly unitRequests: readonly QualificationUnitRequest[];
  readonly catalog: readonly QualificationRequirement[];
  readonly reviews: readonly QualificationReview[];
  readonly personalDocuments: readonly DocumentaryDocument[];
  readonly observedAt: string;
  readonly serviceAttention?: readonly {
    readonly id: string;
    readonly assignmentId: string;
    readonly kind: string;
    readonly status: string;
    readonly scheduledAt: string;
    readonly scheduledEndAt: string | null;
  }[];
}
export interface QualificationCommand {
  readonly expectedVersion: number;
  readonly operationKey: string;
  readonly reason: string;
}
export interface QualificationReviewsCommand extends QualificationCommand {
  readonly vehicleId: string | null;
  readonly scope: QualificationScope;
  readonly documents: readonly Omit<QualificationReview, 'scope' | 'vehicleId' | 'version'>[];
}
export interface QualificationPeriodCommand extends QualificationCommand {
  readonly vehicleId: string;
  readonly kind: UnitKind;
  readonly startAt: string | null;
  readonly startDate: string | null;
  readonly endAt: string | null;
  readonly endDate: string | null;
}
export interface QualificationPersonalCommand extends QualificationCommand {
  readonly documents: readonly DocumentaryDocument[];
  readonly samePersonConfirmed: true;
  readonly originalReviewed: true;
  readonly reviewReference: string;
}
export interface QualificationVehicleCorrectionCommand extends QualificationCommand {
  readonly vehicleId: string;
  readonly plate: string;
  readonly originalReviewed: true;
  readonly reviewReference: string;
}
