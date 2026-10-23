import { UserRole } from '../auth/auth.models';

export interface PageResult<T> {
  readonly items: readonly T[];
  readonly page: number;
  readonly size: number;
  readonly totalItems: number;
  readonly totalPages: number;
}

export interface PageQuery {
  readonly page?: number;
  readonly size?: number;
  readonly sort?: string;
  readonly search?: string;
  readonly active?: boolean;
}

export interface ManagedUser {
  readonly id: string;
  readonly organizationId: string;
  readonly email: string;
  readonly fullName: string;
  readonly phone?: string | null;
  readonly role: UserRole;
  readonly active: boolean;
  readonly lastLoginAt?: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface UserCreatePayload {
  readonly email: string;
  readonly password: string;
  readonly fullName: string;
  readonly phone?: string;
  readonly role: Exclude<UserRole, 'SUPER_ADMIN'>;
}

export interface UserUpdatePayload {
  readonly email: string;
  readonly fullName: string;
  readonly phone?: string;
  readonly role: Exclude<UserRole, 'SUPER_ADMIN'>;
}

export type RecoveryEvidenceMethod = 'PRIOR_SECURE_CONTACT' | 'IN_PERSON';

export interface ConductorRecoveryStartPayload {
  readonly newEmail: string;
  readonly evidenceMethod: RecoveryEvidenceMethod;
  readonly evidenceReference: string;
  readonly reason: string;
  readonly expedienteReviewed: boolean;
  readonly independentEvidenceConfirmed: boolean;
  readonly passwordResetRequired: true;
}

export interface ConductorRecoveryStarted {
  readonly recoveryId: string;
  readonly status: string;
  readonly verificationToken: string;
  readonly otpExpiresAt: string;
  readonly challengeExpiresAt: string;
  readonly emailSent: boolean;
  readonly passwordResetRequired: boolean;
}

export interface ConductorRecoveryStatus {
  readonly recoveryId: string;
  readonly organizationId: string;
  readonly conductorUserId: string;
  readonly authorizedByAdminId: string;
  readonly previousEmail: string | null;
  readonly requestedEmail: string | null;
  readonly evidenceMethod: RecoveryEvidenceMethod;
  readonly evidenceReference: string | null;
  readonly reason: string | null;
  readonly expedienteReviewed: boolean;
  readonly independentEvidenceConfirmed: boolean;
  readonly passwordResetRequired: boolean;
  readonly status: string;
  readonly emailVerifiedAt?: string | null;
  readonly passwordResetExpiresAt?: string | null;
  readonly passwordResetConsumedAt?: string | null;
  readonly completedAt?: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface TransportGroup {
  readonly id: string;
  readonly name: string;
  readonly description?: string | null;
  readonly active: boolean;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly version?: number;
}

export interface GroupPayload {
  readonly name: string;
  readonly description?: string;
  readonly active?: boolean;
}

export interface GroupUpdatePayload extends GroupPayload {
  readonly active: boolean;
  readonly expectedVersion: number;
}

export interface GroupResources {
  readonly groupId: string;
  readonly version: number;
  readonly active: boolean;
  readonly memberships: readonly {
    readonly resourceId: string;
    readonly kind: 'DRIVER' | 'VEHICLE';
    readonly active: boolean;
    readonly source: string;
    readonly recordedAt: string;
  }[];
  readonly commitments: readonly {
    readonly assignmentId: string;
    readonly status: string;
    readonly attentionKind?: string | null;
    readonly scheduledAt: string;
    readonly scheduledEndAt: string;
    readonly groupReferenceSource: string;
  }[];
  readonly history: readonly {
    readonly id: string;
    readonly actorId: string;
    readonly resourceKind: string;
    readonly resourceId: string;
    readonly active: boolean;
    readonly resultingVersion: number;
    readonly occurredAt: string;
  }[];
  readonly observedAt: string;
}

export interface GroupMembershipPayload {
  readonly expectedVersion: number;
  readonly operationKey: string;
  readonly reason: string;
  readonly resourceKind: 'DRIVER' | 'VEHICLE';
  readonly resourceId: string;
  readonly active: boolean;
}

export const DRIVER_STATUSES = [
  'DISPONIBLE',
  'RESERVADO',
  'EN_SERVICIO',
  'ATENCION',
  'DESCANSO',
  'NO_DISPONIBLE',
] as const;

export type DriverStatus = (typeof DRIVER_STATUSES)[number];

export interface Driver {
  readonly id: string;
  readonly userId?: string | null;
  readonly groupId: string;
  readonly groupName: string;
  readonly fullName: string;
  readonly phone?: string | null;
  readonly documentType: string | null;
  readonly documentNumber: string | null;
  readonly licenseNumber?: string | null;
  readonly availabilityStatus: DriverStatus;
  readonly locationConsent: boolean;
  readonly active: boolean;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface LinkedVehicle {
  readonly vehicleId: string;
  readonly plate: string;
  readonly brand?: string | null;
  readonly model?: string | null;
  readonly status: VehicleStatus;
  readonly primary: boolean;
  readonly linkedAt: string;
}

export interface DriverDetail {
  readonly driver: Driver;
  readonly vehicles: readonly LinkedVehicle[];
}

export interface DriverIncorporationStatus {
  readonly driverId: string;
  readonly accountId: string | null;
  readonly requestId: string | null;
  readonly incorporationStatus: string;
  readonly personalApproved: boolean;
  readonly accountProvisioned: boolean;
  readonly accountEnabled: boolean;
  readonly identityReviewed: boolean;
  readonly groupActive: boolean;
  readonly driverEnabled: boolean;
  readonly personalDocumentsStatus: 'CURRENT' | 'EXPIRED' | 'UNKNOWN' | 'REVIEW_REQUIRED';
  readonly activeVehicleLinks: number;
  readonly unitCardinalitySatisfied: boolean;
  readonly operationalEnabled: boolean;
  readonly blockers: readonly string[];
  readonly observedAt: string;
}

export interface DriverPayload {
  readonly userId?: string | null;
  readonly groupId: string;
  readonly fullName: string;
  readonly phone?: string;
  readonly documentType: string;
  readonly documentNumber: string;
  readonly licenseNumber?: string;
}

export interface DriverIdentityReviewPayload {
  readonly identityReview: {
    readonly documentType: 'DNI';
    readonly documentNumber: string;
    readonly method: 'IN_PERSON' | 'PRIOR_SECURE_CONTACT';
    readonly reference: string;
    readonly originalReviewed: true;
    readonly identityConfirmed: true;
  };
  readonly reason: string;
}

export interface DriverIdentityReviewStatus {
  readonly userId: string;
  readonly identityVerified: boolean;
}

export const VEHICLE_STATUSES = [
  'DISPONIBLE',
  'EN_SERVICIO',
  'ATENCION',
  'MANTENIMIENTO',
  'INACTIVO',
] as const;

export type VehicleStatus = (typeof VEHICLE_STATUSES)[number];

export interface Vehicle {
  readonly id: string;
  readonly plate: string;
  readonly brand?: string | null;
  readonly model?: string | null;
  readonly year?: number | null;
  readonly color?: string | null;
  readonly status: VehicleStatus;
  readonly active: boolean;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface VehiclePayload {
  readonly plate: string;
  readonly brand?: string;
  readonly model?: string;
  readonly year?: number | null;
  readonly color?: string;
}

export interface Organization {
  readonly id: string;
  readonly legalName: string;
  readonly tradeName?: string | null;
  readonly status: OrganizationStatus;
  readonly timezone: string;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export const ORGANIZATION_STATUSES = ['ACTIVE', 'SUSPENDED', 'INACTIVE'] as const;

export type OrganizationStatus = (typeof ORGANIZATION_STATUSES)[number];

export interface OrganizationPayload {
  readonly legalName: string;
  readonly tradeName?: string;
  readonly timezone: string;
  readonly status?: OrganizationStatus;
}

export interface OrganizationUpdatePayload extends OrganizationPayload {
  readonly status: OrganizationStatus;
}
