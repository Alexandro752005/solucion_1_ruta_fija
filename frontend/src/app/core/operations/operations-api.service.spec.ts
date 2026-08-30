import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { OperationsApiService } from './operations-api.service';

describe('OperationsApiService', () => {
  let service: OperationsApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(OperationsApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    TestBed.resetTestingModule();
  });

  it('crea una asignacion SCHEDULED directamente y conserva la clave de idempotencia', async () => {
    const created = firstValueFrom(service.createAssignment({
      driverId: 'driver-1',
      vehicleId: 'vehicle-1',
      originText: 'Terminal Norte',
      destinationText: 'Centro',
      scheduledAt: '2026-08-30T13:00:00Z',
      scheduledEndAt: '2026-08-30T14:00:00Z',
    }, 'assignment-unique-key'));

    const request = http.expectOne('/api/v1/assignments');
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.get('Idempotency-Key')).toBe('assignment-unique-key');
    expect(request.request.body).toMatchObject({
      driverId: 'driver-1',
      vehicleId: 'vehicle-1',
      originText: 'Terminal Norte',
    });
    request.flush({ id: 'assignment-1', status: 'SCHEDULED', version: 0 });

    await expect(created).resolves.toMatchObject({ id: 'assignment-1', status: 'SCHEDULED' });
  });

  it('consulta reportes persistidos con rango y solicita un ticket efimero para tiempo real', async () => {
    const assignmentReport = firstValueFrom(service.assignmentReport('2026-08-01', '2026-08-29'));
    const assignmentRequest = http.expectOne('/api/v1/reports/assignments?from=2026-08-01&to=2026-08-29');
    expect(assignmentRequest.request.method).toBe('GET');
    assignmentRequest.flush({ from: '2026-08-01', to: '2026-08-29', totalsByStatus: [], items: [] });

    const incidentReport = firstValueFrom(service.incidentReport('2026-08-01', '2026-08-29'));
    const incidentRequest = http.expectOne('/api/v1/reports/incidents?from=2026-08-01&to=2026-08-29');
    expect(incidentRequest.request.method).toBe('GET');
    incidentRequest.flush({ from: '2026-08-01', to: '2026-08-29', totalsByStatus: [], totalsByCategory: [], items: [] });

    const ticket = firstValueFrom(service.issueStreamTicket());
    const ticketRequest = http.expectOne('/api/v1/operations/stream-ticket');
    expect(ticketRequest.request.method).toBe('POST');
    expect(ticketRequest.request.body).toEqual({});
    ticketRequest.flush({ ticket: 'one-time-ticket', expiresAt: '2026-08-29T23:00:00Z' });

    await expect(assignmentReport).resolves.toMatchObject({ items: [] });
    await expect(incidentReport).resolves.toMatchObject({ items: [] });
    await expect(ticket).resolves.toMatchObject({ ticket: 'one-time-ticket' });
  });

  it('solicita exportaciones reales al backend en PDF y Excel', async () => {
    const xlsx = firstValueFrom(service.exportAssignmentReport('2026-08-01', '2026-08-29', 'xlsx'));
    const xlsxRequest = http.expectOne('/api/v1/reports/assignments/export?from=2026-08-01&to=2026-08-29&format=xlsx');
    expect(xlsxRequest.request.responseType).toBe('blob');
    xlsxRequest.flush(new Blob(['PK']));

    const pdf = firstValueFrom(service.exportAvailabilityReport('pdf'));
    const pdfRequest = http.expectOne('/api/v1/reports/availability/export?format=pdf');
    expect(pdfRequest.request.responseType).toBe('blob');
    pdfRequest.flush(new Blob(['%PDF']));

    await expect(xlsx).resolves.toBeInstanceOf(Blob);
    await expect(pdf).resolves.toBeInstanceOf(Blob);
  });
});
