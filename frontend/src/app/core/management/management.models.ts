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

export interface GroupCoordinator {
  readonly userId: string;
  readonly fullName: string;
  readonly email: string;
  readonly assignedAt: string;
}

export interface TransportGroup {
  readonly id: string;
  readonly name: string;
  readonly description?: string | null;
  readonly active: boolean;
  readonly coordinators: readonly GroupCoordinator[];
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface GroupPayload {
  readonly name: string;
  readonly description?: string;
  readonly active?: boolean;
}

export interface GroupUpdatePayload extends GroupPayload {
  readonly active: boolean;
}

export const DRIVER_STATUSES = [
  'DISPONIBLE',
  'RESERVADO',
  'EN_SERVICIO',
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
  readonly documentType: string;
  readonly documentNumber: string;
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

export interface DriverPayload {
  readonly userId?: string | null;
  readonly groupId: string;
  readonly fullName: string;
  readonly phone?: string;
  readonly documentType: string;
  readonly documentNumber: string;
  readonly licenseNumber?: string;
}

export const VEHICLE_STATUSES = [
  'DISPONIBLE',
  'EN_SERVICIO',
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
