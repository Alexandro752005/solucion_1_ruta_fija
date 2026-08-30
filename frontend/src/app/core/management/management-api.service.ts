import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { RuntimeConfigService } from '../config/runtime-config.service';
import {
  Driver,
  DriverDetail,
  DriverPayload,
  DriverStatus,
  GroupPayload,
  GroupUpdatePayload,
  ManagedUser,
  Organization,
  OrganizationPayload,
  OrganizationUpdatePayload,
  PageQuery,
  PageResult,
  TransportGroup,
  UserCreatePayload,
  UserUpdatePayload,
  Vehicle,
  VehiclePayload,
  VehicleStatus,
} from './management.models';

@Injectable({ providedIn: 'root' })
export class ManagementApiService {
  private readonly http = inject(HttpClient);
  private readonly runtimeConfig = inject(RuntimeConfigService);

  listUsers(
    query: PageQuery & { readonly role?: string } = {},
  ): Observable<PageResult<ManagedUser>> {
    return this.http.get<PageResult<ManagedUser>>(this.url('/users'), {
      params: this.params(query),
    });
  }

  createUser(payload: UserCreatePayload): Observable<ManagedUser> {
    return this.http.post<ManagedUser>(this.url('/users'), payload);
  }

  updateUser(id: string, payload: UserUpdatePayload): Observable<ManagedUser> {
    return this.http.patch<ManagedUser>(this.url(`/users/${id}`), payload);
  }

  setUserActive(id: string, active: boolean): Observable<ManagedUser> {
    return this.http.post<ManagedUser>(
      this.url(`/users/${id}/${active ? 'activate' : 'deactivate'}`),
      {},
    );
  }

  listGroups(query: PageQuery = {}): Observable<PageResult<TransportGroup>> {
    return this.http.get<PageResult<TransportGroup>>(this.url('/groups'), {
      params: this.params(query),
    });
  }

  createGroup(payload: GroupPayload): Observable<TransportGroup> {
    return this.http.post<TransportGroup>(this.url('/groups'), payload);
  }

  updateGroup(id: string, payload: GroupUpdatePayload): Observable<TransportGroup> {
    return this.http.patch<TransportGroup>(this.url(`/groups/${id}`), payload);
  }

  assignCoordinator(groupId: string, userId: string): Observable<TransportGroup> {
    return this.http.post<TransportGroup>(
      this.url(`/groups/${groupId}/coordinators`),
      { userId },
    );
  }

  removeCoordinator(groupId: string, userId: string): Observable<void> {
    return this.http.delete<void>(
      this.url(`/groups/${groupId}/coordinators/${userId}`),
    );
  }

  listDrivers(
    query: PageQuery & {
      readonly groupId?: string;
      readonly status?: DriverStatus;
    } = {},
  ): Observable<PageResult<Driver>> {
    return this.http.get<PageResult<Driver>>(this.url('/drivers'), {
      params: this.params(query),
    });
  }

  getDriver(id: string): Observable<DriverDetail> {
    return this.http.get<DriverDetail>(this.url(`/drivers/${id}`));
  }

  createDriver(payload: DriverPayload): Observable<Driver> {
    return this.http.post<Driver>(this.url('/drivers'), payload);
  }

  updateDriver(id: string, payload: DriverPayload): Observable<Driver> {
    return this.http.patch<Driver>(this.url(`/drivers/${id}`), payload);
  }

  setDriverActive(id: string, active: boolean): Observable<Driver> {
    return this.http.post<Driver>(
      this.url(`/drivers/${id}/${active ? 'activate' : 'deactivate'}`),
      {},
    );
  }

  linkVehicle(driverId: string, vehicleId: string): Observable<DriverDetail> {
    return this.http.post<DriverDetail>(
      this.url(`/drivers/${driverId}/vehicles/${vehicleId}`),
      {},
    );
  }

  unlinkVehicle(driverId: string, vehicleId: string): Observable<void> {
    return this.http.delete<void>(
      this.url(`/drivers/${driverId}/vehicles/${vehicleId}`),
    );
  }

  markPrimaryVehicle(driverId: string, vehicleId: string): Observable<DriverDetail> {
    return this.http.post<DriverDetail>(
      this.url(`/drivers/${driverId}/vehicles/${vehicleId}/primary`),
      {},
    );
  }

  listVehicles(
    query: PageQuery & { readonly status?: VehicleStatus } = {},
  ): Observable<PageResult<Vehicle>> {
    return this.http.get<PageResult<Vehicle>>(this.url('/vehicles'), {
      params: this.params(query),
    });
  }

  createVehicle(payload: VehiclePayload): Observable<Vehicle> {
    return this.http.post<Vehicle>(this.url('/vehicles'), payload);
  }

  updateVehicle(id: string, payload: VehiclePayload): Observable<Vehicle> {
    return this.http.patch<Vehicle>(this.url(`/vehicles/${id}`), payload);
  }

  setVehicleStatus(id: string, status: VehicleStatus): Observable<Vehicle> {
    return this.http.post<Vehicle>(this.url(`/vehicles/${id}/status`), { status });
  }

  listOrganizations(query: PageQuery = {}): Observable<PageResult<Organization>> {
    return this.http.get<PageResult<Organization>>(this.url('/organizations'), {
      params: this.params(query),
    });
  }

  createOrganization(payload: OrganizationPayload): Observable<Organization> {
    return this.http.post<Organization>(this.url('/organizations'), payload);
  }

  updateOrganization(
    id: string,
    payload: OrganizationUpdatePayload,
  ): Observable<Organization> {
    return this.http.patch<Organization>(
      this.url(`/organizations/${id}`),
      payload,
    );
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
