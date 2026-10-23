import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { RuntimeConfigService } from '../config/runtime-config.service';
import { AdminTermsApiService } from './admin-terms-api.service';

describe('AdminTermsApiService', () => {
  let service: AdminTermsApiService;
  let http: HttpTestingController;
  let environment = 'local';

  beforeEach(() => {
    environment = 'local';
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: RuntimeConfigService,
          useValue: {
            apiUrl: (path: string) => '/api/v1' + path,
            config: () => ({ environment }),
          },
        },
      ],
    });
    service = TestBed.inject(AdminTermsApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    TestBed.resetTestingModule();
  });

  it('consulta la versión vigente en el endpoint aprobado', async () => {
    const result = firstValueFrom(service.getCurrentTerms());
    const request = http.expectOne('/api/v1/admin/terms/current');
    expect(request.request.method).toBe('GET');
    request.flush(documentFixture());

    await expect(result).resolves.toMatchObject({
      version: '1.0.0',
      contentSha256: 'a'.repeat(64),
      accepted: false,
    });
  });

  it('envía exactamente la versión y la huella recibidas con confirmación expresa', async () => {
    const result = firstValueFrom(
      service.acceptCurrentTerms(documentFixture()),
    );
    const request = http.expectOne('/api/v1/admin/terms/acceptances');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      version: '1.0.0',
      contentSha256: 'a'.repeat(64),
      accepted: true,
    });
    request.flush({
      acceptanceId: 'acceptance-1',
      adminUserId: 'admin-1',
      organizationId: 'organization-1',
      documentVersion: '1.0.0',
      contentSha256: 'a'.repeat(64),
      synthetic: false,
      acceptedAt: '2026-10-14T12:00:00Z',
    });

    await expect(result).resolves.toMatchObject({
      documentVersion: '1.0.0',
      contentSha256: 'a'.repeat(64),
    });
  });

  it('bloquea documentos sintéticos fuera del entorno test', async () => {
    const result = firstValueFrom(service.getCurrentTerms());
    http.expectOne('/api/v1/admin/terms/current').flush({
      ...documentFixture(),
      synthetic: true,
      version: 'SYNTHETIC-TEST-ONLY-0001',
      content: 'SYNTHETIC TEST DOCUMENT ONLY.',
    });

    await expect(result).rejects.toMatchObject({
      name: 'SyntheticTermsUnavailableError',
    });

    const acceptance = firstValueFrom(
      service.acceptCurrentTerms({
        ...documentFixture(),
        synthetic: true,
        version: 'SYNTHETIC-TEST-ONLY-0001',
      }),
    );
    await expect(acceptance).rejects.toMatchObject({
      name: 'SyntheticTermsUnavailableError',
    });
    http.expectNone('/api/v1/admin/terms/acceptances');
  });

  it('permite documentos sintéticos solo cuando el entorno es test', async () => {
    environment = 'test';
    const synthetic = {
      ...documentFixture(),
      synthetic: true,
      version: 'SYNTHETIC-TEST-ONLY-0001',
      content: 'SYNTHETIC TEST DOCUMENT ONLY.',
    };
    const result = firstValueFrom(service.getCurrentTerms());
    http.expectOne('/api/v1/admin/terms/current').flush(synthetic);
    await expect(result).resolves.toMatchObject({ synthetic: true });
  });

  function documentFixture() {
    return {
      documentKey: 'ADMIN_TERMS',
      version: '1.0.0',
      content: 'Published terms test fixture.',
      contentSha256: 'a'.repeat(64),
      synthetic: false,
      accepted: false,
      acceptedAt: null,
    };
  }
});
