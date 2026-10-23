import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { RuntimeConfigService } from '../config/runtime-config.service';
export interface DashboardPriority { readonly order: number; readonly code: string; readonly count: number; readonly route: string; }
export interface AdminDashboard { readonly generatedAt: string; readonly upcomingUntil: string; readonly priorities: readonly DashboardPriority[]; readonly servicesInProgress: number; readonly activeDrivers: number; readonly activeVehicles: number; }
export interface PlatformDashboard { readonly generatedAt: string; readonly organizations: number; readonly activeOrganizations: number; readonly activeAdmins: number; readonly pendingAdmins: number; }
@Injectable({ providedIn: 'root' })
export class DashboardApiService {
  private readonly http = inject(HttpClient); private readonly config = inject(RuntimeConfigService);
  admin() { return this.http.get<AdminDashboard>(this.config.apiUrl('/dashboard/admin')); }
  platform() { return this.http.get<PlatformDashboard>(this.config.apiUrl('/platform/dashboard')); }
}
