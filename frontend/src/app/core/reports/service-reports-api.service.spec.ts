import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ServiceReportsApiService } from './service-reports-api.service';

describe('ServiceReports API contract (HTTP double)', () => {
  let service: ServiceReportsApiService; let http: HttpTestingController;
  beforeEach(() => { TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] }); service = TestBed.inject(ServiceReportsApiService); http = TestBed.inject(HttpTestingController); });
  afterEach(() => { http.verify(); TestBed.resetTestingModule(); });
  it('queries canonical services without a client-selected organization', async () => {
    const result = firstValueFrom(service.current('2026-10-01','2026-10-21'));
    const r = http.expectOne('/api/v1/reports/services?from=2026-10-01&to=2026-10-21'); expect(r.request.method).toBe('GET'); expect(r.request.params.has('organizationId')).toBe(false); r.flush({ scope: 'ORGANIZATION' }); await expect(result).resolves.toMatchObject({ scope: 'ORGANIZATION' });
  });
  it('preserves the caller operation key on issue and recovery', async () => {
    const command = { operationKey: 'synthetic-stable-key', from: '2026-10-01', to: '2026-10-21' };
    for (let n=0;n<2;n++) { const result = firstValueFrom(service.issue(command)); const r=http.expectOne('/api/v1/reports/services/emissions'); expect(r.request.method).toBe('POST'); expect(r.request.body).toEqual(command); r.flush({ id:'same-emission' }); await expect(result).resolves.toMatchObject({ id:'same-emission' }); }
  });
  it('downloads all formats using the same persisted emission endpoint', async () => {
    for (const format of ['csv','xlsx','pdf'] as const) { const result=firstValueFrom(service.export('same-id',format)); const r=http.expectOne(`/api/v1/reports/services/emissions/same-id/export?format=${format}`); expect(r.request.responseType).toBe('blob'); r.flush(new Blob(['SYNTHETIC'])); expect(await result).toBeInstanceOf(Blob); }
  });
  it('lists revisions and reads the selected immutable version', async () => {
    const list = firstValueFrom(service.list('2026-10-01','2026-10-21')); http.expectOne('/api/v1/reports/services/emissions?from=2026-10-01&to=2026-10-21').flush([{id:'v1'}]); await expect(list).resolves.toEqual([{id:'v1'}]);
    const read = firstValueFrom(service.read('v1')); http.expectOne('/api/v1/reports/services/emissions/v1').flush({id:'v1',revision:1}); await expect(read).resolves.toMatchObject({id:'v1',revision:1});
  });
});
