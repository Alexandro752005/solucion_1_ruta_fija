import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { map, timeout } from 'rxjs';
import { SKIP_AUTH_REFRESH } from '../auth/auth-http-context';
import { RuntimeConfigService } from '../config/runtime-config.service';
import { parseLiveMapGroups, parseLiveMapSnapshot } from './live-map.models';

@Injectable({ providedIn: 'root' })
export class LiveMapApiService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(RuntimeConfigService);
  vehicles(limit: number, groupId: string, driverName: string) {
    let params = new HttpParams().set('limit', limit);
    if (groupId) params = params.set('groupId', groupId);
    if (driverName.trim()) params = params.set('driverName', driverName.trim());
    return this.http.get<unknown>(this.config.apiUrl('/live-map/vehicles'), { params,
      context: new HttpContext().set(SKIP_AUTH_REFRESH, true) }).pipe(timeout(8000), map(value => parseLiveMapSnapshot(value, limit)));
  }
  groups() {
    return this.http.get<unknown>(this.config.apiUrl('/live-map/groups'), { context: new HttpContext().set(SKIP_AUTH_REFRESH, true) }).pipe(timeout(8000), map(parseLiveMapGroups));
  }
}
