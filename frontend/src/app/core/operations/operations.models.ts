import { DriverStatus, VehicleStatus } from '../management/management.models';

export const ASSIGNMENT_STATUSES = [
  'SCHEDULED',
  'EN_SERVICIO',
  'COMPLETED',
  'CANCELLED',
] as const;

export type AssignmentStatus = (typeof ASSIGNMENT_STATUSES)[number];

export interface Assignment {
  readonly id: string;
  readonly status: AssignmentStatus;
  readonly version: number;
  readonly driverId: string;
  readonly driverName: string;
  readonly groupId: string;
  readonly groupName: string;
  readonly vehicleId: string;
  readonly vehiclePlate: string;
  readonly originText: string;
  readonly destinationText: string;
  readonly scheduledAt: string;
  readonly scheduledEndAt: string;
  readonly reservedAt?: string | null;
  readonly startedAt?: string | null;
  readonly completedAt?: string | null;
  readonly cancelledAt?: string | null;
  readonly cancellationReason?: string | null;
  readonly notes?: string | null;
  readonly createdById: string;
  readonly createdByName: string;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface AssignmentPayload {
  readonly driverId: string;
  readonly vehicleId: string;
  readonly originText: string;
  readonly destinationText: string;
  readonly scheduledAt: string;
  readonly scheduledEndAt: string;
  readonly notes?: string;
}

export interface AssignmentUpdatePayload extends AssignmentPayload {
  readonly version: number;
}

export interface AssignmentVersionPayload {
  readonly version: number;
}

export interface AssignmentCancelPayload extends AssignmentVersionPayload {
  readonly reason: string;
}

export const INCIDENT_CATEGORIES = [
  'AVERIA',
  'ACCIDENTE',
  'RETRASO',
  'OTRO',
] as const;

export type IncidentCategory = (typeof INCIDENT_CATEGORIES)[number];

export const INCIDENT_STATUSES = ['OPEN', 'FOLLOW_UP', 'RESOLVED'] as const;

export type IncidentStatus = (typeof INCIDENT_STATUSES)[number];

export interface Incident {
  readonly id: string;
  readonly version: number;
  readonly driverId: string;
  readonly driverName: string;
  readonly assignmentId?: string | null;
  readonly category: IncidentCategory;
  readonly status: IncidentStatus;
  readonly description: string;
  readonly reportedById: string;
  readonly reportedByName: string;
  readonly reportedAt: string;
  readonly followUpNote?: string | null;
  readonly followedUpById?: string | null;
  readonly followedUpByName?: string | null;
  readonly followedUpAt?: string | null;
  readonly resolvedAt?: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface IncidentPayload {
  readonly driverId: string;
  readonly assignmentId?: string;
  readonly category: IncidentCategory;
  readonly description: string;
}

export interface IncidentFollowUpPayload {
  readonly version: number;
  readonly note: string;
  readonly resolve: boolean;
}

export const ANNOUNCEMENT_AUDIENCES = ['ORGANIZATION', 'GROUP'] as const;

export type AnnouncementAudience = (typeof ANNOUNCEMENT_AUDIENCES)[number];

export interface Announcement {
  readonly id: string;
  readonly title: string;
  readonly body: string;
  readonly audienceType: AnnouncementAudience;
  readonly audienceId?: string | null;
  readonly requireReadAck: boolean;
  readonly createdById: string;
  readonly createdByName: string;
  readonly createdAt: string;
}

export interface AnnouncementPayload {
  readonly title: string;
  readonly body: string;
  readonly audienceType: AnnouncementAudience;
  readonly audienceId?: string;
  readonly requireReadAck?: false;
}

export interface StatusCount {
  readonly status: string;
  readonly total: number;
}

export interface AvailabilityReport {
  readonly generatedAt: string;
  readonly drivers: readonly StatusCount[];
  readonly vehicles: readonly StatusCount[];
  readonly futureScheduledAssignments: number;
  readonly assignmentsInService: number;
  readonly openIncidents: number;
}

export interface AssignmentReport {
  readonly from: string;
  readonly to: string;
  readonly totalsByStatus: readonly StatusCount[];
  readonly items: readonly Assignment[];
}

export interface IncidentReport {
  readonly from: string;
  readonly to: string;
  readonly totalsByStatus: readonly StatusCount[];
  readonly totalsByCategory: readonly StatusCount[];
  readonly items: readonly Incident[];
}

export const REPORT_EXPORT_FORMATS = ['pdf', 'xlsx'] as const;

export type ReportExportFormat = (typeof REPORT_EXPORT_FORMATS)[number];

export interface OperationStreamTicket {
  readonly ticket: string;
  readonly expiresAt: string;
}

export interface OperationRealtimeEvent {
  readonly event: string;
  readonly occurredAt: string;
  readonly data: Readonly<Record<string, unknown>>;
}

export type AdministrativeDriverStatus = Extract<
  DriverStatus,
  'DISPONIBLE' | 'DESCANSO' | 'NO_DISPONIBLE'
>;

export type OperationalVehicleStatus = VehicleStatus;
