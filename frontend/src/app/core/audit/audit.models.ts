import { PageQuery, PageResult } from '../management/management.models';

export interface AuditEvent {
  readonly id: string;
  readonly userId?: string | null;
  readonly userFullName?: string | null;
  readonly action: string;
  readonly entityType?: string | null;
  readonly entityId?: string | null;
  readonly correlationId?: string | null;
  readonly metadata: Readonly<Record<string, unknown>>;
  readonly occurredAt: string;
}

export interface AuditEventQuery extends PageQuery {
  readonly action?: string;
  readonly entityType?: string;
  readonly from?: string;
  readonly to?: string;
}

export type AuditEventPage = PageResult<AuditEvent>;
