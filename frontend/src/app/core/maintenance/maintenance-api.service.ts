import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { RuntimeConfigService } from '../config/runtime-config.service';
import { SKIP_AUTH_REFRESH } from '../auth/auth-http-context';

export interface MaintenanceRecord {
  readonly id: string; readonly kind: 'PREVENTIVE' | 'URGENT'; readonly status: 'OPEN' | 'WORK_COMPLETED' | 'RETURNED' | 'CANCELLED';
  readonly startsAt: string; readonly plannedEndsAt: string | null; readonly blockingEndsAt: string | null;
  readonly createdBy: string; readonly createdAt: string; readonly workCompletedBy: string | null;
  readonly workCompletedAt: string | null; readonly returnedBy: string | null; readonly returnedAt: string | null;
}
export interface MaintenanceView {
  readonly vehicleId: string; readonly version: number; readonly mechanicallyBlockedNow: boolean; readonly observedAt: string;
  readonly records: readonly MaintenanceRecord[];
  readonly history: readonly { readonly id: string; readonly action: string; readonly recordId: string | null; readonly actorId: string; readonly resultingVersion: number; readonly occurredAt: string }[];
}
export interface MaintenancePreview {
  readonly vehicleId: string; readonly blockingStartsAt: string; readonly canConfirmPreventive: boolean;
  readonly commitments: readonly { readonly assignmentId: string; readonly driverId: string; readonly status: string; readonly scheduledAt: string; readonly scheduledEndAt: string }[];
}
export interface MaintenanceBasic { readonly expectedVersion: number; readonly operationKey: string; readonly reason: string; }
@Injectable({ providedIn: 'root' })
export class MaintenanceApiService {
  private readonly http = inject(HttpClient); private readonly config = inject(RuntimeConfigService);
  private url(id: string, suffix = '') { return this.config.apiUrl(`/vehicles/${encodeURIComponent(id)}/maintenance${suffix}`); }
  private options() { return { context: new HttpContext().set(SKIP_AUTH_REFRESH, true) }; }
  read(id: string) { return this.http.get<MaintenanceView>(this.url(id)); }
  preview(id: string, startDate: string) { return this.http.get<MaintenancePreview>(this.url(id, '/preview'), { params: { startDate } }); }
  create(id: string, c: MaintenanceBasic & { readonly kind: 'PREVENTIVE' | 'URGENT'; readonly startDate: string | null; readonly endDate: string | null }) { return this.http.post<MaintenanceView>(this.url(id), c, this.options()); }
  work(id: string, record: string, c: MaintenanceBasic & { readonly responsibleReference: string; readonly evidenceReference: string; readonly workCompleted: true }) { return this.http.post<MaintenanceView>(this.url(id, `/${encodeURIComponent(record)}/work-completed`), c, this.options()); }
  cancel(id: string, record: string, c: MaintenanceBasic) { return this.http.post<MaintenanceView>(this.url(id, `/${encodeURIComponent(record)}/cancel`), c, this.options()); }
  authorizeReturn(id: string, c: MaintenanceBasic) { return this.http.post<MaintenanceView>(this.url(id, '/return'), c, this.options()); }
}
