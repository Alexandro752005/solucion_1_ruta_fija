import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { RuntimeConfigService } from '../config/runtime-config.service';
import { PageQuery, PageResult } from '../management/management.models';
import {
  AdministrativeDriverStatus,
  Announcement,
  AnnouncementAudience,
  AnnouncementPayload,
  Assignment,
  AssignmentCancelPayload,
  AssignmentPayload,
  AssignmentStatus,
  AssignmentUpdatePayload,
  AssignmentVersionPayload,
  AssignmentReport,
  AvailabilityReport,
  Incident,
  IncidentCategory,
  IncidentFollowUpPayload,
  IncidentPayload,
  IncidentReport,
  IncidentStatus,
  OperationStreamTicket,
  ReportExportFormat,
} from './operations.models';

@Injectable({ providedIn: 'root' })
export class OperationsApiService {
  private readonly http = inject(HttpClient);
  private readonly runtimeConfig = inject(RuntimeConfigService);

  listAssignments(
    query: PageQuery & {
      readonly driverId?: string;
      readonly vehicleId?: string;
      readonly status?: AssignmentStatus;
      readonly from?: string;
      readonly to?: string;
    } = {},
  ): Observable<PageResult<Assignment>> {
    return this.http.get<PageResult<Assignment>>(this.url('/assignments'), {
      params: this.params(query),
    });
  }

  createAssignment(
    payload: AssignmentPayload,
    idempotencyKey?: string,
  ): Observable<Assignment> {
    const headers = idempotencyKey === undefined
      ? undefined
      : new HttpHeaders({ 'Idempotency-Key': idempotencyKey });
    return this.http.post<Assignment>(this.url('/assignments'), payload, { headers });
  }

  updateAssignment(id: string, payload: AssignmentUpdatePayload): Observable<Assignment> {
    return this.http.patch<Assignment>(this.url(`/assignments/${id}`), payload);
  }

  reserveAssignment(id: string, payload: AssignmentVersionPayload): Observable<Assignment> {
    return this.http.post<Assignment>(this.url(`/assignments/${id}/reserve`), payload);
  }

  startAssignment(id: string, payload: AssignmentVersionPayload): Observable<Assignment> {
    return this.http.post<Assignment>(this.url(`/assignments/${id}/start`), payload);
  }

  completeAssignment(id: string, payload: AssignmentVersionPayload): Observable<Assignment> {
    return this.http.post<Assignment>(this.url(`/assignments/${id}/complete`), payload);
  }

  cancelAssignment(id: string, payload: AssignmentCancelPayload): Observable<Assignment> {
    return this.http.post<Assignment>(this.url(`/assignments/${id}/cancel`), payload);
  }

  changeDriverAvailability(id: string, status: AdministrativeDriverStatus): Observable<unknown> {
    return this.http.post(this.url(`/drivers/${id}/availability`), { status });
  }

  listIncidents(
    query: PageQuery & {
      readonly driverId?: string;
      readonly status?: IncidentStatus;
      readonly category?: IncidentCategory;
      readonly from?: string;
      readonly to?: string;
    } = {},
  ): Observable<PageResult<Incident>> {
    return this.http.get<PageResult<Incident>>(this.url('/incidents'), {
      params: this.params(query),
    });
  }

  createIncident(payload: IncidentPayload): Observable<Incident> {
    return this.http.post<Incident>(this.url('/incidents'), payload);
  }

  followUpIncident(id: string, payload: IncidentFollowUpPayload): Observable<Incident> {
    return this.http.patch<Incident>(this.url(`/incidents/${id}/follow-up`), payload);
  }

  listAnnouncements(
    query: PageQuery & { readonly audienceType?: AnnouncementAudience } = {},
  ): Observable<PageResult<Announcement>> {
    return this.http.get<PageResult<Announcement>>(this.url('/announcements'), {
      params: this.params(query),
    });
  }

  createAnnouncement(payload: AnnouncementPayload): Observable<Announcement> {
    return this.http.post<Announcement>(this.url('/announcements'), payload);
  }

  availabilityReport(): Observable<AvailabilityReport> {
    return this.http.get<AvailabilityReport>(this.url('/reports/availability'));
  }

  assignmentReport(from: string, to: string): Observable<AssignmentReport> {
    return this.http.get<AssignmentReport>(this.url('/reports/assignments'), {
      params: new HttpParams().set('from', from).set('to', to),
    });
  }

  incidentReport(from: string, to: string): Observable<IncidentReport> {
    return this.http.get<IncidentReport>(this.url('/reports/incidents'), {
      params: new HttpParams().set('from', from).set('to', to),
    });
  }

  exportAvailabilityReport(format: ReportExportFormat): Observable<Blob> {
    return this.http.get(this.url('/reports/availability/export'), {
      params: new HttpParams().set('format', format),
      responseType: 'blob',
    });
  }

  exportAssignmentReport(from: string, to: string, format: ReportExportFormat): Observable<Blob> {
    return this.http.get(this.url('/reports/assignments/export'), {
      params: new HttpParams().set('from', from).set('to', to).set('format', format),
      responseType: 'blob',
    });
  }

  exportIncidentReport(from: string, to: string, format: ReportExportFormat): Observable<Blob> {
    return this.http.get(this.url('/reports/incidents/export'), {
      params: new HttpParams().set('from', from).set('to', to).set('format', format),
      responseType: 'blob',
    });
  }

  issueStreamTicket(): Observable<OperationStreamTicket> {
    return this.http.post<OperationStreamTicket>(this.url('/operations/stream-ticket'), {});
  }

  private url(path: string): string {
    return this.runtimeConfig.apiUrl(path);
  }

  private params<T extends object>(values: T): HttpParams {
    const query = values as Record<string, string | number | boolean | undefined>;
    return Object.entries(query).reduce(
      (params, [key, value]) =>
        value === undefined || value === ''
          ? params
          : params.set(key, String(value)),
      new HttpParams(),
    );
  }
}
