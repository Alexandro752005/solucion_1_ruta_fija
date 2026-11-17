import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { EMPTY, of } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { escapeCsvCell, ReportsPage } from './reports.page';
import { ServiceReportsComponent } from './service-reports.component';
import { OperationsApiService } from '../../core/operations/operations-api.service';
import { OperationRealtimeService } from '../../core/operations/operation-realtime.service';
import { ApiErrorService } from '../../core/api/api-error.service';
import { AuthSessionService } from '../../core/auth/auth-session.service';

@Component({ selector: 'rf-service-reports', template: '' })
class SeparateServiceReportsStub {}

describe('escapeCsvCell', () => {
  it('neutraliza formulas antes de crear un CSV', () => {
    expect(escapeCsvCell('=HYPERLINK("https://malicious.example")')).toBe(
      '"\'=HYPERLINK(""https://malicious.example"")"',
    );
    expect(escapeCsvCell(' +SUM(A1:A2)')).toBe('"\' +SUM(A1:A2)"');
  });

  it('conserva los valores ordinarios y escapa comillas', () => {
    expect(escapeCsvCell('Terminal "Norte"')).toBe('"Terminal ""Norte"""');
  });
});

describe('assignment report totals from the current backend contract', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('renders all nine totals and keeps interruption and transfer counts distinct', async () => {
    const totals = [
      'PENDING_RESPONSE', 'SCHEDULED', 'EN_SERVICIO', 'COMPLETED', 'REJECTED',
      'CANCELLED', 'EXPIRED', 'INTERRUPTED', 'TRANSFERRED',
    ].map(status => ({ status, total: status === 'INTERRUPTED' ? 2 : status === 'TRANSFERRED' ? 3 : 0 }));
    const api = {
      availabilityReport: () => of({ generatedAt: '2026-11-14T12:00:00Z', drivers: [], vehicles: [],
        futureScheduledAssignments: 0, assignmentsInService: 0, openIncidents: 0 }),
      assignmentReport: () => of({ from: '2026-11-01', to: '2026-11-02', totalsByStatus: totals, items: [] }),
      incidentReport: () => of({ from: '2026-11-01', to: '2026-11-02', totalsByStatus: [], totalsByCategory: [], items: [] }),
    };
    const realtime = { connect: vi.fn(), disconnect: vi.fn(), events: EMPTY };
    await TestBed.configureTestingModule({ imports: [ReportsPage], providers: [
      { provide: OperationsApiService, useValue: api },
      { provide: OperationRealtimeService, useValue: realtime },
      { provide: ApiErrorService, useValue: { toUiError: () => ({ message: 'SYNTHETIC_ERROR', fieldErrors: [] }) } },
      { provide: AuthSessionService, useValue: { user: () => null } },
    ] }).overrideComponent(ReportsPage, { remove: { imports: [ServiceReportsComponent] },
      add: { imports: [SeparateServiceReportsStub] } }).compileComponents();
    const fixture = TestBed.createComponent(ReportsPage);
    fixture.detectChanges();
    const root: HTMLElement = fixture.nativeElement;
    const rendered = [...root.querySelectorAll('[aria-labelledby="assignment-report-title"] .report-totals span')];
    expect(rendered).toHaveLength(9);
    expect(rendered.map(node => node.textContent?.trim())).toContain('2 Interrumpida; atención pendiente');
    expect(rendered.map(node => node.textContent?.trim())).toContain('3 Tramo relevado');
    expect(fixture.componentInstance.assignmentReport()?.totalsByStatus).toEqual(totals);
    fixture.destroy();
    expect(realtime.disconnect).toHaveBeenCalledOnce();
  });
});
