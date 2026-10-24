import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { InvitationsApiService } from './invitations-api.service';

describe('InvitationsApiService', () => {
  let service: InvitationsApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(InvitationsApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    TestBed.resetTestingModule();
  });

  it('emite con el correo solicitado y recibe el código solo en la respuesta de emisión', async () => {
    const result = firstValueFrom(service.issue({ recipientEmail: 'driver@example.test' }));
    const request = http.expectOne('/api/v1/invitations');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ recipientEmail: 'driver@example.test' });
    request.flush({
      id: 'invitation-1',
      recipientEmail: 'driver@example.test',
      code: '123456',
      issuedAt: '2026-10-14T12:00:00Z',
      expiresAt: '2026-10-19T12:00:00Z',
    });
    await expect(result).resolves.toMatchObject({ id: 'invitation-1', code: '123456' });
  });

  it('consulta la página solicitada y usa únicamente los campos del resumen', async () => {
    const result = firstValueFrom(service.list(2, 20));
    const request = http.expectOne('/api/v1/invitations?page=2&size=20');
    expect(request.request.method).toBe('GET');
    request.flush({
      items: [summary()],
      page: 2,
      size: 20,
      totalItems: 41,
      totalPages: 3,
    });
    await expect(result).resolves.toMatchObject({
      page: 2,
      totalItems: 41,
      items: [{ id: 'invitation-1', revokedAt: null, usedAt: null }],
    });
    expect('code' in (await result).items[0]).toBe(false);
  });

  it('revoca por id sin añadir flujos de consumo ni aceptación', async () => {
    const result = firstValueFrom(service.revoke('invitation-1'));
    const request = http.expectOne('/api/v1/invitations/invitation-1/revoke');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({});
    request.flush({ ...summary(), revokedAt: '2026-10-14T12:30:00Z' });
    await expect(result).resolves.toMatchObject({ revokedAt: '2026-10-14T12:30:00Z' });
    expect('validate' in service).toBe(false);
    expect('accept' in service).toBe(false);
  });

  function summary() {
    return {
      id: 'invitation-1',
      recipientEmail: 'driver@example.test',
      issuedAt: '2026-10-14T12:00:00Z',
      expiresAt: '2026-10-19T12:00:00Z',
      revokedAt: null,
      usedAt: null,
    };
  }
});
