import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { ManagementApiService } from './management-api.service';

describe('ManagementApiService', () => {
  let service: ManagementApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ManagementApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    TestBed.resetTestingModule();
  });

  it('consulta conductores con filtros y paginación sin enviar valores vacíos', async () => {
    const result = firstValueFrom(
      service.listDrivers({
        page: 1,
        size: 20,
        sort: 'fullName,asc',
        search: 'Ana',
        groupId: 'group-1',
        status: 'DISPONIBLE',
      }),
    );

    const request = http.expectOne('/api/v1/drivers?page=1&size=20&sort=fullName,asc&search=Ana&groupId=group-1&status=DISPONIBLE');
    expect(request.request.method).toBe('GET');
    request.flush({ items: [], page: 1, size: 20, totalItems: 0, totalPages: 0 });

    await expect(result).resolves.toMatchObject({ page: 1, items: [] });
  });

  it('usa rutas específicas para vincular vehículos y cambiar estados administrativos', async () => {
    const linked = firstValueFrom(service.linkVehicle('driver-1', 'vehicle-1'));
    const linkRequest = http.expectOne('/api/v1/drivers/driver-1/vehicles/vehicle-1');
    expect(linkRequest.request.method).toBe('POST');
    expect(linkRequest.request.body).toEqual({});
    linkRequest.flush({ driver: {}, vehicles: [] });
    await expect(linked).resolves.toMatchObject({ vehicles: [] });

    const changed = firstValueFrom(service.setVehicleStatus('vehicle-1', 'MANTENIMIENTO'));
    const statusRequest = http.expectOne('/api/v1/vehicles/vehicle-1/status');
    expect(statusRequest.request.method).toBe('POST');
    expect(statusRequest.request.body).toEqual({ status: 'MANTENIMIENTO' });
    statusRequest.flush({ id: 'vehicle-1', status: 'MANTENIMIENTO' });
    await expect(changed).resolves.toMatchObject({ status: 'MANTENIMIENTO' });
  });

  it('no conserva operaciones HTTP para vínculos de grupo retirados', () => {
    expect('assignCoordinator' in service).toBe(false);
    expect('removeCoordinator' in service).toBe(false);
  });
});
