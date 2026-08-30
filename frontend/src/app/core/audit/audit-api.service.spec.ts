import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { AuditApiService } from './audit-api.service';

describe('AuditApiService', () => {
  let service: AuditApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(AuditApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    TestBed.resetTestingModule();
  });

  it('consulta auditoría paginada con filtros y sin rutas mutables', async () => {
    const result = firstValueFrom(service.list({
      page: 0,
      size: 20,
      sort: 'occurredAt,desc',
      action: 'ASSIGNMENT_STARTED',
      entityType: 'ASSIGNMENT',
    }));
    const request = http.expectOne('/api/v1/audit-events?page=0&size=20&sort=occurredAt,desc&action=ASSIGNMENT_STARTED&entityType=ASSIGNMENT');
    expect(request.request.method).toBe('GET');
    request.flush({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 });

    await expect(result).resolves.toMatchObject({ items: [], totalItems: 0 });
  });
});
