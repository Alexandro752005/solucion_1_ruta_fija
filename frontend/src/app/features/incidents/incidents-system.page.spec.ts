import { TestBed } from '@angular/core/testing';
import { NEVER, of } from 'rxjs';
import { describe, expect, it } from 'vitest';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { ApiErrorService } from '../../core/api/api-error.service';
import { ManagementApiService } from '../../core/management/management-api.service';
import { OperationsApiService } from '../../core/operations/operations-api.service';
import { OperationRealtimeService } from '../../core/operations/operation-realtime.service';
import { Incident } from '../../core/operations/operations.models';
import { IncidentsPage } from './incidents.page';
import { IncidentWorkflowApiService } from '../../core/operations/incident-workflow-api.service';

describe('System validity incident attribution', () => {
  it('renders Sistema without assigning a fictitious human account', async () => {
    const incident: Incident = { id: 'synthetic-incident', version: 0, driverId: 'synthetic-driver',
      driverName: 'CONDUCTOR-synthetic-driver', assignmentId: 'synthetic-assignment', category: 'OTRO',
      source: 'SYSTEM', status: 'OPEN', description: 'Registro sintético por vigencia', reportedById: null,
      reportedByName: 'Sistema', reportedAt: '2026-10-19T10:00:00Z', createdAt: '2026-10-19T10:00:00Z', updatedAt: '2026-10-19T10:00:00Z' };
    const empty = { items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 };
    await TestBed.configureTestingModule({ imports: [IncidentsPage], providers: [
      {provide:IncidentWorkflowApiService,useValue:{history:()=>of([]),candidates:()=>of([])}},
      { provide: OperationsApiService, useValue: { listIncidents: () => of({ ...empty, items: [incident], totalItems: 1, totalPages: 1 }), listAssignments: () => of(empty) } },
      { provide: ManagementApiService, useValue: { listDrivers: () => of(empty) } },
      { provide: AuthSessionService, useValue: { user: () => ({ organizationName: 'SYNTHETIC' }) } },
      { provide: ApiErrorService, useValue: { toUiError: () => ({ message: 'Synthetic failure' }) } },
      { provide: OperationRealtimeService, useValue: { connect: () => {}, disconnect: () => {}, events: NEVER } },
    ] }).compileComponents();
    const fixture = TestBed.createComponent(IncidentsPage); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Sistema');
    expect(fixture.nativeElement.textContent).not.toContain('CUENTA-null');
    expect(fixture.nativeElement.textContent).not.toContain('CUENTA-undefined');
    fixture.destroy(); TestBed.resetTestingModule();
  });
});
